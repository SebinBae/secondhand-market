"""골든셋 품질 평가.

실제 OpenAI를 호출한다(케이스당 LLM 4회). **비용이 발생하므로 CI에서 돌리지 않는다.**

    uv run --env-file .env python eval/run_eval.py

결과는 표준출력 요약 + eval/results/<타임스탬프>.json 으로 남는다.
이 스크립트는 측정만 한다 — 점수를 올리려고 케이스나 프롬프트를 고치지 않는다.
"""

import json
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.config import settings  # noqa: E402
from app.copy_rules import BANNED_EXPRESSIONS, DESCRIPTION_MAX, TITLE_MAX  # noqa: E402
from app.graph import run_listing_draft  # noqa: E402
from app.llm import CategoryResult, LLMClient, OpenAILLMClient, VisionSummary  # noqa: E402
from app.schemas import ListingDraftRequest  # noqa: E402

EVAL_DIR = Path(__file__).resolve().parent
CASES_PATH = EVAL_DIR / "golden" / "cases.json"
RESULTS_DIR = EVAL_DIR / "results"


class ProbeLLM:
    """케이스별 내부 동작을 관찰하기 위한 얇은 래퍼.

    그래프는 응답만 돌려주므로 재분류 분기가 실제로 탔는지 밖에서 알 수 없다.
    성공한 CategoryResult 호출 수만 세면 `category_node` 실행 횟수와 정확히 같다
    (`_invoke`의 실패 재시도는 예외를 던지므로 여기까지 오지 않는다) → 재분류 횟수 = 호출 수 - 1.
    """

    def __init__(self, inner: LLMClient) -> None:
        self._inner = inner
        self.category_calls = 0
        self.identifiable: bool | None = None
        self.identifiable_reason: str | None = None

    def reset(self) -> None:
        self.category_calls = 0
        self.identifiable = None
        self.identifiable_reason = None

    def structured(self, *, instructions, text, schema, image_urls=()):
        result = self._inner.structured(
            instructions=instructions, text=text, schema=schema, image_urls=image_urls
        )
        if schema is CategoryResult:
            self.category_calls += 1
        elif schema is VisionSummary:
            self.identifiable = result.identifiable
            self.identifiable_reason = result.identifiableReason
        return result


def check_format(title: str, description: str) -> list[str]:
    """형식 위반 사유 목록. 비어 있으면 준수."""
    violations = []
    if len(title) > TITLE_MAX:
        violations.append(f"제목 {len(title)}자 > {TITLE_MAX}")
    if len(description) > DESCRIPTION_MAX:
        violations.append(f"설명 {len(description)}자 > {DESCRIPTION_MAX}")
    if not title.strip() or not description.strip():
        violations.append("제목/설명 비어 있음")
    for expression in BANNED_EXPRESSIONS:
        if expression in title or expression in description:
            violations.append(f"과장 표현 '{expression}'")
    return violations


def check_keywords(text: str, keyword_groups: list[list[str]]) -> list[list[str]]:
    """충족하지 못한 OR 그룹 목록."""
    return [group for group in keyword_groups if not any(word in text for word in group)]


def check_confidence(expected: str, actual: str) -> bool:
    """expectedConfidence는 HIGH / NOT_HIGH 두 값만 쓴다."""
    return actual == "HIGH" if expected == "HIGH" else actual != "HIGH"


def run_case(case: dict, llm: ProbeLLM) -> dict:
    started = time.monotonic()
    llm.reset()
    result = {
        "id": case["id"],
        "kind": case["kind"],
        "expectedCategory": case["expectedCategory"],
        "expectedConfidence": case["expectedConfidence"],
    }
    # 정답을 정의할 수 없는 케이스는 해당 지표의 분모에서 빠진다(None으로 남긴다).
    price_range = case["expectedPriceRange"]
    keyword_groups = case["requiredKeywords"]

    def probe() -> dict:
        return {
            "identifiable": llm.identifiable,
            "identifiableReason": llm.identifiable_reason,
            "retryCount": max(llm.category_calls - 1, 0),
        }

    try:
        draft = run_listing_draft(
            ListingDraftRequest(imageUrls=[case["imageUrl"]], userHint=case.get("userHint")), llm
        )
    except Exception as error:
        result.update(
            status="error",
            error=f"{type(error).__name__}: {error}",
            elapsedSec=round(time.monotonic() - started, 1),
            **probe(),
            categoryCorrect=False if case["expectedCategory"] else None,
            confidenceOk=False,
            priceInRange=False if price_range else None,
            formatOk=False,
            keywordsOk=False if keyword_groups else None,
        )
        return result

    amount = draft.suggestedPrice.amount
    format_violations = check_format(draft.title, draft.description)
    missing = check_keywords(f"{draft.title} {draft.description}", keyword_groups)

    result.update(
        status="ok",
        elapsedSec=round(time.monotonic() - started, 1),
        **probe(),
        actualCategory=draft.category.value,
        categoryCorrect=(
            draft.category.value == case["expectedCategory"] if case["expectedCategory"] else None
        ),
        confidence=draft.confidence.value,
        confidenceOk=check_confidence(case["expectedConfidence"], draft.confidence.value),
        amount=amount,
        priceInRange=price_range[0] <= amount <= price_range[1] if price_range else None,
        formatOk=not format_violations,
        formatViolations=format_violations,
        keywordsOk=not missing if keyword_groups else None,
        missingKeywordGroups=missing,
        title=draft.title,
        description=draft.description,
        rationale=draft.suggestedPrice.rationale,
    )
    return result


def rate(results: list[dict], key: str) -> tuple[int, int]:
    """(충족 개수, 적용 대상 개수). None은 정답을 정의할 수 없는 케이스이므로 분모에서 뺀다."""
    applicable = [r for r in results if r.get(key) is not None]
    return sum(1 for r in applicable if r[key]), len(applicable)


def main() -> int:
    cases = json.loads(CASES_PATH.read_text(encoding="utf-8"))["cases"]
    llm = ProbeLLM(OpenAILLMClient())

    results = []
    for index, case in enumerate(cases, start=1):
        print(f"[{index}/{len(cases)}] {case['id']} ... ", end="", flush=True)
        result = run_case(case, llm)
        results.append(result)
        if result["status"] == "error":
            print(f"오류 ({result['error'][:60]})")
        else:
            if result["categoryCorrect"] is None:
                mark = f"카테고리 정답없음(→{result['actualCategory']})"
            elif result["categoryCorrect"]:
                mark = "정답"
            else:
                mark = f"오답→{result['actualCategory']}"
            confidence = result["confidence"] + ("" if result["confidenceOk"] else " ✗")
            print(f"{mark} / {confidence} / {result['amount']:,}원 / {result['elapsedSec']}s")

    metrics = {
        "categoryAccuracy": rate(results, "categoryCorrect"),
        "confidenceAccuracy": rate(results, "confidenceOk"),
        "priceInRangeRate": rate(results, "priceInRange"),
        "formatComplianceRate": rate(results, "formatOk"),
        "keywordCoverageRate": rate(results, "keywordsOk"),
    }

    print("\n=== 지표 ===")
    for name, (hit, total) in metrics.items():
        print(f"{name:22} {hit:2}/{total}  ({hit / total:.0%})")

    retried = [r for r in results if r["retryCount"] > 0]
    unidentifiable = [r for r in results if r["identifiable"] is False]
    print(f"\n재분류 트리거 {len(retried)}건: {', '.join(r['id'] for r in retried) or '없음'}")
    print(
        f"Vision 식별 불가 {len(unidentifiable)}건: "
        f"{', '.join(r['id'] for r in unidentifiable) or '없음'}"
    )

    errors = [r for r in results if r["status"] == "error"]
    if errors:
        print(f"\n오류 케이스 {len(errors)}건: {', '.join(r['id'] for r in errors)}")

    RESULTS_DIR.mkdir(exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output = RESULTS_DIR / f"{stamp}.json"
    output.write_text(
        json.dumps(
            {"model": settings.AI_MODEL, "ranAt": stamp, "metrics": metrics, "results": results},
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"\n결과 저장: {output.relative_to(EVAL_DIR.parent)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
