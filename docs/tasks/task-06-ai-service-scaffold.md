# task-06: ai-service 스캐폴드 + API 계약

- 브랜치: `feat/ai-service-scaffold`
- 커밋 타입: `feat:`
- 선행 조건: 없음 (task-05와 병렬 가능)

## 배경

AI 등록 어시스턴트를 담을 Python 서비스의 뼈대. 이 작업은 **그래프 구현 없이** 서비스 골격과 API 계약만 확정한다.

## 설계 결정 (변경 금지)

- 위치: 저장소 루트 `ai-service/` (모노레포)
- 스택: Python 3.12, FastAPI, LangGraph, uv (패키지 관리)
- 호출 방향: React → Spring → ai-service (프론트가 ai-service를 직접 호출하지 않음 — 인증·검증은 Spring 일원화)
- LLM: OpenAI API, 모델명·키는 환경변수 (`OPENAI_API_KEY`, `AI_MODEL`) <!-- Anthropic→OpenAI, 사용자 결정 -->>

## API 계약 (이 형태로 고정, 변경 시 사용자 확인)

```
POST /internal/v1/listing-drafts
Request:  { "imageUrls": ["https://..."], "userHint": "소니 카메라 팝니다" }
Response: {
  "category": "DIGITAL",
  "title": "...", "description": "...",
  "suggestedPrice": { "amount": 350000, "rationale": "..." },
  "confidence": "HIGH|MEDIUM|LOW"
}
오류: { "code": "...", "message": "..." } + 적절한 상태코드
GET /internal/v1/health → 200
```

## 작업 단계

1. `ai-service/` 디렉토리: pyproject.toml(uv), FastAPI 앱, 위 API의 Pydantic 스키마, health 엔드포인트
2. listing-drafts는 **스텁 응답**(고정 더미)으로 구현 — 그래프는 task-07
3. `ai-service/CLAUDE.md` 작성: 스택, 노드 단일 책임 원칙, 구조화 출력 강제, 금지사항(임의 엔드포인트 추가 금지)
4. `Dockerfile` + 루트 docker-compose에 `ai-service` 추가 (내부 네트워크, 8000)
5. pytest: 스키마 검증·health 테스트. CI에 ai-service 테스트 job 추가 (paths 필터로 백엔드와 분리)

## 하지 말 것

- LangGraph 그래프/LLM 호출 구현 금지 (task-07)
- 카테고리 enum은 백엔드 `ProductCategory`와 동일 값 사용 — 새 값 발명 금지

## 완료 조건 (DoD)

- [ ] `docker compose up` 후 Spring 컨테이너에서 ai-service health 호출 성공
- [ ] 스텁 응답이 API 계약 스키마와 일치함이 pytest로 증명됨
- [ ] CI에서 ai-service 테스트가 별도 job으로 실행됨
- [ ] `ai-service/CLAUDE.md` 존재
