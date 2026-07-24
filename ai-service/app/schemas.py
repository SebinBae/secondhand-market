"""API 계약 스키마.

카테고리 enum은 백엔드 `ProductCategory`와 동일한 값을 사용한다(새 값 발명 금지).
"""

from enum import Enum

from pydantic import BaseModel, Field


class Category(str, Enum):
    """백엔드 domain.product.type.ProductCategory 와 1:1."""

    DIGITAL = "DIGITAL"
    FURNITURE = "FURNITURE"
    CLOTHING = "CLOTHING"
    BOOK = "BOOK"
    KITCHEN_UTENSIL = "KITCHEN_UTENSIL"


class Confidence(str, Enum):
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"


class ListingDraftRequest(BaseModel):
    imageUrls: list[str] = Field(..., min_length=1)
    userHint: str | None = None


class SuggestedPrice(BaseModel):
    amount: int
    rationale: str


class ListingDraftResponse(BaseModel):
    category: Category
    title: str
    description: str
    suggestedPrice: SuggestedPrice
    confidence: Confidence


class ErrorResponse(BaseModel):
    code: str
    message: str
