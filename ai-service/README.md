# ai-service

AI 등록 어시스턴트("사진 → 상품 초안") 서비스. FastAPI + LangGraph, 패키지 관리는 uv.

React → Spring → ai-service 구조에서 **Spring이 내부 호출**하는 서비스이며, 외부에 직접 노출하지 않는다. Spring 연동은 task-08.

## 그래프

```
intake → vision → category → price_estimation → description → END
                     ↓ (신뢰도 LOW, 1회만)
                   retry ──┘ (힌트를 vision 요약으로 보강해 category 재실행)
```

노드는 각각 상태의 한 조각만 채운다. 분기는 `category` 하나뿐이고, 재분류 후에도 신뢰도가 LOW면 계약된 오류로 끝낸다. 모든 LLM 호출은 Pydantic 스키마를 넘기는 구조화 출력이며, 호출 실패·파싱 실패는 해당 노드에서 1회 재시도한다.

LLM 호출부는 `app/llm.py`의 `LLMClient` 프로토콜로 분리돼 있어, 테스트는 OpenAI 호출 없이 가짜 구현으로 돈다.

## 오류 응답

| 상태 | code | 상황 |
|---|---|---|
| 502 | `AI_UPSTREAM_ERROR` | LLM 호출·파싱이 재시도 후에도 실패 |
| 422 | `CATEGORY_LOW_CONFIDENCE` | 재분류 후에도 카테고리 신뢰도가 LOW |

## 실행

```bash
uv sync                                              # 의존성 설치
uv run pytest                                        # 테스트
uv run uvicorn app.main:app --reload --port 8000     # 로컬 실행
```

Docker(모노레포 루트에서 전체 기동):
```bash
docker compose up -d          # backend 스택 + ai-service
```

## 품질 평가 (골든셋)

```bash
uv run --env-file .env python eval/run_eval.py
```

`eval/golden/cases.json`의 12케이스를 실행해 카테고리 정확도·가격 범위 적중률·형식 준수율을 출력하고, 결과를 `eval/results/<타임스탬프>.json`에 남긴다. 기준선 수치와 해석은 [docs/measurements/ai-quality-baseline.md](../docs/measurements/ai-quality-baseline.md)에 있다.

> **비용 주의**: 실제 OpenAI를 호출한다(1회 실행에 LLM 48회). **CI에서 실행하지 않는다.** 점수를 올리려고 골든셋 케이스를 고치지 않는다.

## API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/internal/v1/health` | 헬스체크 → `{"status":"ok"}` |
| POST | `/internal/v1/listing-drafts` | 이미지·힌트로 상품 초안 생성 |

`POST /internal/v1/listing-drafts`
```json
// 요청
{ "imageUrls": ["https://..."], "userHint": "소니 카메라 팝니다" }
// 응답
{ "category": "DIGITAL", "title": "...", "description": "...",
  "suggestedPrice": { "amount": 350000, "rationale": "..." },
  "confidence": "HIGH|MEDIUM|LOW" }
```

## 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `OPENAI_API_KEY` | (없음) | OpenAI 키. 초안 생성에 필요 |
| `AI_MODEL` | `gpt-4o` | 사용할 모델명. 이미지 입력을 지원하는 모델이어야 한다 |

키가 없어도 서비스는 기동되고 health는 응답한다. 초안 생성 호출 시점에 실패한다.
