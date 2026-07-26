# 작업 문서 (Claude Code용)

세션 시작 시 해당 task 파일 하나를 컨텍스트로 제공한다.

```
docs/tasks/task-05-product-image-upload.md 읽고 작업 진행해줘.
루트 CLAUDE.md의 규칙을 준수하고, 문서와 충돌하는 판단이 필요하면 먼저 질문할 것.
```

## 공통 규칙

- 루트 `CLAUDE.md` 준수. Phase 범위·완료 조건은 `docs/roadmap.md`를 따른다.
- 브랜치 1개 = 세션 1개 = PR 1개. 문서에 명시된 브랜치명 사용.
- DoD 전부 만족 전에 작업을 끝내지 않는다. 불가능 판단 시 이유 보고.
- 테스트/규칙 코드를 수정해서 통과시키는 우회 금지.
- 종료 시 PR 본문 "AI 도구 사용 기록"용 요약 출력.

## 배치 1 — 준비 작업 (완료: 2026-07-24)

| 문서 | PR | 상태 |
|---|---|---|
| task-01-exception-relocation | #23 | ✅ |
| task-02-module-boundary-archunit | #24 | ✅ |
| task-03-application-event | #25 | ✅ |
| task-04-k6-baseline | #26 | ✅ |

## 배치 2 — AI 상품 등록 어시스턴트 (2~3주차)

| 순서 | 문서 | 브랜치 | 선행 조건 |
|---|---|---|---|
| 1 | task-05-product-image-upload.md | feat/product-image-upload | 없음 |
| 2 | task-06-ai-service-scaffold.md | feat/ai-service-scaffold | 없음 (05와 병렬 가능) |
| 3 | task-07-listing-draft-graph.md | feat/listing-draft-graph | task-06 머지 |
| 4 | task-08-ai-assist-integration.md | feat/ai-assist-integration | task-05, 07 머지 |
| 5 | task-09-golden-set-eval.md | chore/golden-set-eval | task-07 머지 |

배치 3(3~4주차: LIKE 검색 보강)부터는 착수 시점에 작성.
