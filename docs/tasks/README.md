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

## 배치 2 — AI 상품 등록 어시스턴트 (완료: 2026-07-26)

| 문서 | PR | 상태 |
|---|---|---|
| task-05-product-image-upload | #29 | ✅ |
| task-06-ai-service-scaffold | #31 | ✅ |
| task-07-listing-draft-graph | #33 | ✅ |
| task-08-ai-assist-integration | #34 | ✅ |
| task-09-golden-set-eval | #35 | ✅ |

배치 중 발견해 분리 처리한 것: 컨테이너용 docker 프로필 부재(#30).

## 배치 3 — AI 신뢰도 보정 (완료: 2026-07-27)

task-09 기준선에서 12케이스 전부 `HIGH`가 나와, task-07의 저신뢰 재분류 분기가 죽어 있음이 드러났다. 측정 도구를 먼저 갖춘 뒤 고친다 — 순서를 바꾸면 "케이스를 통과하기 쉽게 맞췄다"를 반박할 수 없다.

| 순서 | 문서 | PR | 상태 |
|---|---|---|---|
| 1 | task-10-confidence-goldenset-extension | #37 | ✅ |
| 2 | task-11-confidence-calibration | #38 | ✅ |

결과: 신뢰도 적중률 76% → 88% (`docs/measurements/ai-quality-baseline.md`).

## 보류 — AI 카테고리 확장 (task-12)

task-11에서 미뤄둔 항목. 카테고리가 5종뿐이라 자전거처럼 멀쩡히 팔 수 있는 물건이 초안 없이 422로 끝난다.

**착수하지 않기로 했다(2026-07-27).** 문서(`task-12-category-expansion.md`)는 판단 근거를 남겨두기 위해 유지한다 — 추가할 3종의 선정 기준, `ETC`를 넣지 않는 이유, 카테고리를 늘리면 새로 생기는 경계의 판단 규칙, 골든셋 기대값을 어디까지 손대야 하는지가 정리돼 있다. 다시 착수할 때 이 문서부터 읽으면 된다.

다음 배치는 착수 시점에 작성한다.
