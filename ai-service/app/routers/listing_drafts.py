"""상품 초안 생성 엔드포인트.

요청을 LangGraph 그래프에 넘기고, 결과를 API 계약 스키마로 반환한다.
LLM 구현은 의존성으로 주입해 테스트에서 가짜 구현으로 바꿀 수 있게 한다.
"""

from functools import lru_cache

from fastapi import APIRouter, Depends

from app.graph import run_listing_draft
from app.llm import LLMClient, OpenAILLMClient
from app.schemas import ListingDraftRequest, ListingDraftResponse

router = APIRouter()


@lru_cache(maxsize=1)
def get_llm() -> LLMClient:
    return OpenAILLMClient()


@router.post("/internal/v1/listing-drafts", response_model=ListingDraftResponse)
def create_listing_draft(
    request: ListingDraftRequest, llm: LLMClient = Depends(get_llm)
) -> ListingDraftResponse:
    return run_listing_draft(request, llm)
