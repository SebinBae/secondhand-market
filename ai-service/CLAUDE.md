# CLAUDE.md — ai-service

AI 등록 어시스턴트("사진 → 상품 초안") 서비스. 이 문서의 규칙을 반드시 지킨다.

## 스택
- Python 3.12 / **FastAPI**(서빙) / **LangGraph**(에이전트 오케스트레이션) / **uv**(패키지 관리)
- LLM: **OpenAI** (`OPENAI_API_KEY`, `AI_MODEL` 환경변수 주입).

## 아키텍처 규칙
- 호출 방향은 **React → Spring → ai-service**. ai-service는 외부에 직접 노출하지 않으며(인증·검증은 Spring 일원화), Spring이 내부 네트워크로만 호출한다.
- **API 계약은 고정이다.** 아래 엔드포인트 외 **임의 엔드포인트 추가 금지**. 계약 변경은 사용자 확인 후에만.
  - `POST /internal/v1/listing-drafts`
  - `GET /internal/v1/health`
- **카테고리 enum은 백엔드 `ProductCategory`와 동일한 값만 사용**한다(`DIGITAL, FURNITURE, CLOTHING, BOOK, KITCHEN_UTENSIL`). 새 값 발명 금지.

## LangGraph / LLM 원칙
- **노드는 단일 책임**으로 분리한다. 상태(State) 스키마를 먼저 설계한 뒤 노드를 구현한다.
- LLM 출력은 **구조화 출력을 강제**한다(OpenAI structured outputs / JSON schema). 자유 텍스트 파싱에 의존하지 않는다.
- 응답은 항상 API 계약 Pydantic 스키마(`app/schemas.py`)로 검증해 반환한다.
- LLM 호출은 `app/llm.py`의 `LLMClient` 프로토콜을 통해서만 한다. 노드가 OpenAI SDK를 직접 import하지 않는다(테스트 대체 가능해야 함).
- **재시도 분기는 `category` 하나뿐이다.** 다른 노드에 분기를 추가하지 않는다.

## 단계 규칙 (미리 구현 금지)
- Spring 연동은 **task-08**, 골든셋 평가는 **task-09**.
- 임베딩·유사 매물 검색은 L2 범위다. 가격 추정은 모델의 일반 지식 + 근거 문장으로만 한다.

## 명령어
```bash
uv sync            # 의존성 설치(.venv 생성)
uv run pytest      # 테스트
uv run uvicorn app.main:app --reload --port 8000   # 로컬 실행
```
