"""LLM 호출 경계.

노드는 이 모듈의 `LLMClient` 프로토콜에만 의존한다. 덕분에 테스트에서 OpenAI 호출 없이
가짜 구현으로 대체할 수 있다. 모든 호출은 Pydantic 스키마를 넘기는 **구조화 출력**이며,
자유 텍스트 파싱에 의존하지 않는다.
"""

from collections.abc import Sequence
from typing import Protocol, TypeVar

from pydantic import BaseModel

from app.config import settings
from app.schemas import Category, Confidence

T = TypeVar("T", bound=BaseModel)


# --- LLM 구조화 출력 스키마 (노드별 1개) ---


class VisionSummary(BaseModel):
    """이미지에서 읽어낸 물건 종류/브랜드/상태 요약.

    `identifiable`은 요약 문장과 **분리된 필드**여야 한다. 불확실성을 문장 안에 녹이면
    다음 노드가 그것을 읽어내지 못하고 확신에 찬 요약만 전달받는다(task-09 실패 원인).
    """

    summary: str
    identifiable: bool
    identifiableReason: str


class CategoryResult(BaseModel):
    category: Category
    confidence: Confidence


class PriceResult(BaseModel):
    amount: int
    rationale: str


class CopyResult(BaseModel):
    title: str
    description: str


# --- 호출 인터페이스 ---


class LLMClient(Protocol):
    def structured(
        self,
        *,
        instructions: str,
        text: str,
        schema: type[T],
        image_urls: Sequence[str] = (),
    ) -> T:
        """`schema` 형태로 파싱된 응답을 반환한다. 실패 시 예외를 던진다."""
        ...


class OpenAILLMClient:
    """OpenAI Responses API 기반 구현. 이미지 입력과 구조화 출력을 함께 지원한다."""

    def __init__(self, api_key: str | None = None, model: str | None = None) -> None:
        from openai import OpenAI

        self._client = OpenAI(api_key=api_key or settings.OPENAI_API_KEY)
        self._model = model or settings.AI_MODEL

    def structured(
        self,
        *,
        instructions: str,
        text: str,
        schema: type[T],
        image_urls: Sequence[str] = (),
    ) -> T:
        content: list[dict] = [{"type": "input_text", "text": text}]
        content += [{"type": "input_image", "image_url": url} for url in image_urls]

        response = self._client.responses.parse(
            model=self._model,
            instructions=instructions,
            input=[{"role": "user", "content": content}],
            text_format=schema,
        )
        parsed = response.output_parsed
        if parsed is None:
            raise ValueError(f"{schema.__name__} 구조화 출력 파싱 실패")
        return parsed
