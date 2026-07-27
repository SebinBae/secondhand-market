"""그래프 상태 스키마.

노드는 자기가 채우는 키만 부분 갱신으로 반환한다(LangGraph가 병합).
"""

from typing import TypedDict

from app.schemas import Category, Confidence, SuggestedPrice


class DraftState(TypedDict, total=False):
    # 입력
    imageUrls: list[str]
    userHint: str | None
    # Vision
    visionSummary: str
    identifiable: bool
    identifiableReason: str
    # Category
    category: Category
    categoryConfidence: Confidence
    # PriceEstimation
    priceSuggestion: SuggestedPrice
    # Description
    title: str
    description: str
    # Category 재분류 횟수 (0 또는 1)
    retryCount: int
