# task-08: 모놀리스 연동 (AI 등록 도우미 API)

- 브랜치: `feat/ai-assist-integration`
- 커밋 타입: `feat:`
- 선행 조건: task-05, task-07 머지

## 배경

Spring이 ai-service를 호출해 등록 초안을 프론트에 제공한다. 핵심 원칙: **AI는 보조 기능** — ai-service가 죽어도 수동 등록은 반드시 동작 (기획서 5.5 가용성).

## 작업 단계

1. `domain/product`에 초안 요청 API: `POST /api/products/listing-drafts` (인증 필요) — 요청: 업로드된 이미지 URL + 힌트, 응답: ai-service 계약과 동일 구조
2. Spring → ai-service 호출: RestClient, 연결 2s/응답 15s 타임아웃
3. 실패 처리: 타임아웃·5xx·계약 불일치 시 오류를 삼키지 말고 명시적 응답 `{ "aiAvailable": false }` 반환 — 프론트가 수동 등록으로 안내할 수 있게
4. ai-service URL은 설정 주입 (`AI_SERVICE_URL`, 기본 http://localhost:8000)
5. 테스트: ai-service 목 서버(WireMock 또는 MockRestServiceServer)로 정상/타임아웃/5xx 경로 검증

## 하지 말 것

- 상품 등록 API 자체에 AI 호출을 끼워 넣지 않기 — 초안 요청과 등록은 별도 API (등록은 AI 없이 항상 가능)
- ai-service 응답을 그대로 저장하지 않기 — 초안은 제안일 뿐, 저장은 사용자가 수정 후 기존 등록 API로

## 완료 조건 (DoD)

- [ ] 정상 경로: 이미지 업로드 → 초안 요청 → 계약 스키마 응답이 로컬 docker compose에서 동작
- [ ] ai-service 컨테이너 중지 상태에서 초안 요청이 `aiAvailable: false`로 응답하고, 상품 수동 등록은 정상 동작 (테스트로 증명)
- [ ] 타임아웃/5xx 경로가 테스트로 증명됨
- [ ] ArchUnit 통과
