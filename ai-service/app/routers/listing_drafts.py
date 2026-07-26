"""상품 초안 생성 엔드포인트.

현재는 API 계약 확정용 **고정 스텁 응답**만 반환한다. 실제 LangGraph 그래프/LLM 호출은 task-07.
"""

from fastapi import APIRouter

from app.schemas import (
    Category,
    Confidence,
    ListingDraftRequest,
    ListingDraftResponse,
    SuggestedPrice,
)

router = APIRouter()


@router.post("/internal/v1/listing-drafts", response_model=ListingDraftResponse)
def create_listing_draft(request: ListingDraftRequest) -> ListingDraftResponse:
    # TODO(task-07): LangGraph 그래프로 이미지 분석 → 실제 초안 생성으로 교체
    return ListingDraftResponse(
        category=Category.DIGITAL,
        title="상품 제목(스텁)",
        description="상품 설명(스텁). 실제 생성은 task-07에서 구현됩니다.",
        suggestedPrice=SuggestedPrice(amount=350000, rationale="유사 매물 기준 추정(스텁)"),
        confidence=Confidence.MEDIUM,
    )
