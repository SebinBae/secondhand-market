"""그래프 노드.

노드 하나는 상태의 한 조각만 채운다. 노드 사이에서 LLM 호출 실패는 `_invoke`가 1회 재시도한
뒤 `LLMCallError`로 바꿔 던지고, 이는 계약된 오류 응답으로 변환된다.
"""

from collections.abc import Sequence
from typing import TypeVar

from pydantic import BaseModel

from app.copy_rules import DESCRIPTION_MAX, TITLE_MAX, enforce_copy_rules
from app.errors import LLMCallError, LowConfidenceError
from app.graph.state import DraftState
from app.llm import CategoryResult, CopyResult, LLMClient, PriceResult, VisionSummary
from app.schemas import Category, Confidence, SuggestedPrice

T = TypeVar("T", bound=BaseModel)

CATEGORY_VALUES = ", ".join(c.value for c in Category)


def _invoke(
    llm: LLMClient,
    *,
    instructions: str,
    text: str,
    schema: type[T],
    image_urls: Sequence[str] = (),
) -> T:
    """구조화 출력 호출. 실패 시 1회만 재시도하고, 그래도 실패하면 계약 오류로 바꾼다."""
    last_error: Exception | None = None
    for _ in range(2):
        try:
            return llm.structured(
                instructions=instructions, text=text, schema=schema, image_urls=image_urls
            )
        except Exception as error:  # noqa: BLE001 - 업스트림 예외 종류를 가리지 않고 재시도한다
            last_error = error
    raise LLMCallError(f"{schema.__name__} 생성 실패: {last_error}")


def intake_node(state: DraftState) -> DraftState:
    """입력 정규화. 빈 이미지 URL과 공백뿐인 힌트를 걸러낸다."""
    image_urls = [url.strip() for url in state["imageUrls"] if url.strip()]
    if not image_urls:
        raise LLMCallError("분석할 이미지 URL이 없습니다")

    hint = (state.get("userHint") or "").strip()
    return {"imageUrls": image_urls, "userHint": hint or None, "retryCount": 0}


def vision_node(state: DraftState, llm: LLMClient) -> DraftState:
    """이미지에서 물건 종류/브랜드/상태와 **식별 가능 여부**를 읽어낸다."""
    hint = state.get("userHint") or "(없음)"
    result = _invoke(
        llm,
        instructions=(
            "너는 중고거래 상품 사진을 분석하는 어시스턴트다. "
            "사진에서 확인 가능한 사실만 적고, 보이지 않는 것은 추측하지 마라. "
            "물건 종류, 브랜드/모델(식별 가능한 경우), 외관 상태와 흠집 여부를 한국어로 요약하라. "
            "그와 별개로, 사진에 찍힌 것이 '어떤 종류의 물건인지' 알 수 있으면 "
            "identifiable=true로 표시한다. 판단 기준은 물건의 종류뿐이다 — "
            "브랜드나 모델명을 알아내지 못한 것은 식별 불가가 아니고, "
            "같은 물건이 여러 개 있거나 배경에 다른 것이 함께 찍힌 것도 식별 불가가 아니다. "
            "초점이 나가 형체를 알아볼 수 없거나, 일부만 찍혀 종류를 단정할 수 없거나, "
            "서로 다른 물건이 뒤섞여 무엇을 파는 것인지 고를 수 없을 때만 false로 표시하고, "
            "identifiableReason에 그렇게 본 이유를 한 문장으로 적는다. "
            "판매자 힌트는 이 판단의 근거로 삼지 마라 — 사진만으로 판단한다."
        ),
        text=f"판매자가 남긴 힌트: {hint}",
        schema=VisionSummary,
        image_urls=state["imageUrls"],
    )
    return {
        "visionSummary": result.summary,
        "identifiable": result.identifiable,
        "identifiableReason": result.identifiableReason,
    }


def cap_confidence(confidence: Confidence, *, identifiable: bool) -> Confidence:
    """식별 불가 사진에서는 HIGH를 허용하지 않는다.

    프롬프트 지시로는 지켜지지 않는다는 것이 기준선 측정에서 드러났으므로 코드로 막는다.
    LOW로 내리지 않고 MEDIUM에서 멈추는 이유: LOW는 재분류 분기를 태우고 그래도 LOW면
    초안 없이 422로 끝난다. 사진이 흐릴 뿐 팔 수 있는 물건인 경우까지 초안을 없애기보다,
    낮은 신뢰도를 붙여 내려주고 판단은 사용자에게 맡긴다. 상한이지 하한이 아니므로
    모델이 스스로 LOW를 냈다면 그대로 둔다.
    """
    if identifiable or confidence is not Confidence.HIGH:
        return confidence
    return Confidence.MEDIUM


def category_node(state: DraftState, llm: LLMClient) -> DraftState:
    """요약을 근거로 카테고리 1개와 신뢰도를 고른다. 신뢰도 상한은 코드가 강제한다."""
    hint = state.get("userHint") or "(없음)"
    identifiable = state["identifiable"]
    result = _invoke(
        llm,
        instructions=(
            f"다음 카테고리 중 정확히 하나를 고른다: {CATEGORY_VALUES}. "
            "목록에 없는 값을 만들지 마라. 근거가 뚜렷하면 HIGH, 애매하면 MEDIUM, "
            "판단이 어려우면 LOW로 신뢰도를 매긴다. "
            "사진만으로 물건을 특정할 수 없다고 표시된 경우에는 HIGH를 쓰지 마라."
        ),
        text=(
            f"상품 요약: {state['visionSummary']}\n"
            f"사진만으로 물건 특정 가능: {'예' if identifiable else '아니오'}"
            f" ({state['identifiableReason']})\n"
            f"판매자 힌트: {hint}"
        ),
        schema=CategoryResult,
    )
    return {
        "category": result.category,
        "categoryConfidence": cap_confidence(result.confidence, identifiable=identifiable),
    }


def retry_node(state: DraftState) -> DraftState:
    """저신뢰 분류를 1회만 다시 시도한다. 재분류 전에 힌트를 요약으로 보강한다."""
    hint = state.get("userHint")
    enriched = f"{hint} / 사진 분석: {state['visionSummary']}" if hint else state["visionSummary"]
    return {"userHint": enriched, "retryCount": state.get("retryCount", 0) + 1}


def route_after_category(state: DraftState) -> str:
    """저신뢰면 1회 재분류, 이미 재분류했는데도 저신뢰면 계약된 오류로 끝낸다."""
    if state["categoryConfidence"] is not Confidence.LOW:
        return "price_estimation"
    if state.get("retryCount", 0) == 0:
        return "retry"
    raise LowConfidenceError("힌트를 보강해 재분류했지만 카테고리 신뢰도가 낮습니다")


def price_estimation_node(state: DraftState, llm: LLMClient) -> DraftState:
    """모델의 일반 지식으로 가격을 추정한다(유사 매물 검색은 L2 범위)."""
    result = _invoke(
        llm,
        instructions=(
            "중고 시세를 아는 범위에서 원화 판매가를 하나 제시한다. "
            "amount는 원 단위 정수이고, rationale에는 그 금액으로 본 근거를 한 문장으로 적는다. "
            "실제 매물을 검색한 것처럼 말하지 마라."
        ),
        text=f"카테고리: {state['category'].value}\n상품 요약: {state['visionSummary']}",
        schema=PriceResult,
    )
    if result.amount <= 0 or not result.rationale.strip():
        raise LLMCallError("가격 추정 결과에 금액 또는 근거가 없습니다")

    return {
        "priceSuggestion": SuggestedPrice(amount=result.amount, rationale=result.rationale.strip())
    }


def description_node(state: DraftState, llm: LLMClient) -> DraftState:
    """제목과 설명을 쓰고, 길이·과장 표현 규칙을 후처리로 강제한다."""
    result = _invoke(
        llm,
        instructions=(
            f"중고거래 판매글의 제목과 설명을 한국어로 쓴다. "
            f"제목은 {TITLE_MAX}자, 설명은 {DESCRIPTION_MAX}자를 넘기지 않는다. "
            "확인되지 않은 사실과 과장 표현을 쓰지 말고, 사진에서 드러난 상태를 그대로 적는다."
        ),
        text=f"카테고리: {state['category'].value}\n상품 요약: {state['visionSummary']}",
        schema=CopyResult,
    )
    title, description = enforce_copy_rules(result.title, result.description)
    if not title or not description:
        raise LLMCallError("제목 또는 설명이 비어 있습니다")

    return {"title": title, "description": description}
