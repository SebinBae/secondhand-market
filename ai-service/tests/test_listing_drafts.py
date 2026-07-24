from fastapi.testclient import TestClient

from app.main import app
from app.schemas import ListingDraftResponse

client = TestClient(app)


def test_listing_draft_stub_matches_contract():
    res = client.post(
        "/internal/v1/listing-drafts",
        json={"imageUrls": ["https://example.com/a.jpg"], "userHint": "소니 카메라 팝니다"},
    )
    assert res.status_code == 200

    # 응답이 API 계약 스키마와 일치하는지 검증 (스키마 위반 시 ValidationError)
    draft = ListingDraftResponse.model_validate(res.json())
    assert draft.category in list(draft.category.__class__)
    assert isinstance(draft.suggestedPrice.amount, int)
    assert draft.confidence in list(draft.confidence.__class__)


def test_listing_draft_requires_image_urls():
    res = client.post("/internal/v1/listing-drafts", json={"userHint": "이미지 없음"})
    assert res.status_code == 422
