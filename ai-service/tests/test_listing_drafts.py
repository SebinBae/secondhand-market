from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from app.llm import CategoryResult, CopyResult
from app.main import app
from app.routers.listing_drafts import get_llm
from app.schemas import Category, Confidence, ListingDraftResponse
from tests.fake_llm import FakeLLM

REQUEST = {"imageUrls": ["https://example.com/a.jpg"], "userHint": "소니 카메라 팝니다"}


@pytest.fixture
def client() -> Iterator[TestClient]:
    # 테스트가 실제 OpenAI 클라이언트를 만들지 않도록 기본값부터 가짜로 둔다.
    app.dependency_overrides[get_llm] = FakeLLM
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()


def use(llm: FakeLLM) -> FakeLLM:
    app.dependency_overrides[get_llm] = lambda: llm
    return llm


def test_정상_경로는_계약_스키마로_응답한다(client: TestClient):
    llm = use(FakeLLM())

    res = client.post("/internal/v1/listing-drafts", json=REQUEST)

    assert res.status_code == 200
    draft = ListingDraftResponse.model_validate(res.json())
    assert draft.category is Category.DIGITAL
    assert draft.confidence is Confidence.HIGH
    assert draft.suggestedPrice.amount == 350000
    assert draft.suggestedPrice.rationale
    # 분류가 한 번에 끝났으므로 재분류는 없다
    assert llm.calls.count("CategoryResult") == 1


def test_저신뢰_분류는_힌트를_보강해_1회_재분류한다(client: TestClient):
    llm = use(
        FakeLLM(
            [
                CategoryResult(category=Category.DIGITAL, confidence=Confidence.LOW),
                CategoryResult(category=Category.DIGITAL, confidence=Confidence.HIGH),
            ]
        )
    )

    res = client.post("/internal/v1/listing-drafts", json=REQUEST)

    assert res.status_code == 200
    assert ListingDraftResponse.model_validate(res.json()).confidence is Confidence.HIGH
    assert llm.calls.count("CategoryResult") == 2
    # 재분류 프롬프트에는 vision 요약이 힌트로 덧붙는다
    assert "사진 분석" in llm.category_prompts[1]


def test_재분류_후에도_저신뢰면_계약된_오류를_반환한다(client: TestClient):
    low = CategoryResult(category=Category.DIGITAL, confidence=Confidence.LOW)
    llm = use(FakeLLM([low, low]))

    res = client.post("/internal/v1/listing-drafts", json=REQUEST)

    assert res.status_code == 422
    assert res.json()["code"] == "CATEGORY_LOW_CONFIDENCE"
    assert res.json()["message"]
    # 재분류는 1회까지만 한다
    assert llm.calls.count("CategoryResult") == 2


def test_LLM_호출_실패는_500이_아닌_계약된_오류가_된다(client: TestClient):
    llm = use(FakeLLM(fail_always=True))

    res = client.post("/internal/v1/listing-drafts", json=REQUEST)

    assert res.status_code == 502
    assert res.json()["code"] == "AI_UPSTREAM_ERROR"
    # 실패한 노드에서 1회만 재시도한다
    assert llm.calls.count("VisionSummary") == 2


def test_제목_설명은_후처리_규칙을_지킨다(client: TestClient):
    use(
        FakeLLM(
            copy_result=CopyResult(
                title="역대급 상태 최저가 소니 A7C 미러리스 카메라 본체 급처분합니다 지금 바로 연락주세요",
                description="완벽한 상태입니다. " + "설명 " * 300,
            )
        )
    )

    res = client.post("/internal/v1/listing-drafts", json=REQUEST)

    draft = ListingDraftResponse.model_validate(res.json())
    assert len(draft.title) <= 40
    assert len(draft.description) <= 500
    assert "역대급" not in draft.title
    assert "최저가" not in draft.title
    assert "완벽" not in draft.description


def test_이미지_URL이_없으면_422다(client: TestClient):
    res = client.post("/internal/v1/listing-drafts", json={"userHint": "이미지 없음"})
    assert res.status_code == 422
