# ai-service

AI 등록 어시스턴트("사진 → 상품 초안") 서비스. FastAPI + LangGraph, 패키지 관리는 uv.

React → Spring → ai-service 구조에서 **Spring이 내부 호출**하는 서비스이며, 외부에 직접 노출하지 않는다. 현재는 스캐폴드 단계로 API 계약과 **고정 스텁 응답**만 제공한다(그래프/LLM은 task-07).

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

## API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/internal/v1/health` | 헬스체크 → `{"status":"ok"}` |
| POST | `/internal/v1/listing-drafts` | 이미지·힌트로 상품 초안 생성(현재 스텁) |

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
| `OPENAI_API_KEY` | (없음) | OpenAI 키. 스캐폴드에선 미사용, task-07부터 필요 |
| `AI_MODEL` | `gpt-4o` | 사용할 모델명 |

값이 없어도 서비스는 기동된다(LLM 호출이 없는 스캐폴드 단계).
