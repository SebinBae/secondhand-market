"""테스트용 가짜 LLM.

노드가 `LLMClient` 프로토콜에만 의존하므로, 실제 OpenAI 호출 없이 각 노드의 구조화 출력을
원하는 값으로 지정할 수 있다.
"""

from collections.abc import Sequence

from app.llm import CategoryResult, CopyResult, PriceResult, VisionSummary
from app.schemas import Category, Confidence


class FakeLLM:
    def __init__(
        self,
        category_results: Sequence[CategoryResult] | None = None,
        *,
        fail_always: bool = False,
        copy_result: CopyResult | None = None,
    ) -> None:
        self._category_results = list(
            category_results
            or [CategoryResult(category=Category.DIGITAL, confidence=Confidence.HIGH)]
        )
        self._fail_always = fail_always
        self._copy_result = copy_result or CopyResult(
            title="소니 A7C 미러리스 카메라",
            description="사용 흔적이 있는 소니 A7C 본체입니다. 셔터수 약 8천 회, 작동 이상 없습니다.",
        )
        self.calls: list[str] = []
        self.category_prompts: list[str] = []

    def structured(self, *, instructions, text, schema, image_urls: Sequence[str] = ()):
        self.calls.append(schema.__name__)

        if self._fail_always:
            raise RuntimeError("업스트림 호출 실패(테스트)")

        if schema is VisionSummary:
            return VisionSummary(summary="소니 미러리스 카메라 본체, 외관 사용감 있음")
        if schema is CategoryResult:
            self.category_prompts.append(text)
            return self._category_results.pop(0)
        if schema is PriceResult:
            return PriceResult(amount=350000, rationale="동일 모델 중고 시세를 고려한 금액입니다.")
        if schema is CopyResult:
            return self._copy_result

        raise AssertionError(f"예상하지 못한 스키마: {schema}")
