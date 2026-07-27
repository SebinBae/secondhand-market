# main 브랜치 보호 설정

Phase 1 DoD "ModuleBoundaryTest가 CI에서 머지 게이트로 동작"의 근거 문서.

GitHub UI에서만 설정할 수 있어 코드로 남길 수 없으므로, **적용한 값을 여기에 기록**한다. 설정을 바꾸면 이 문서도 함께 갱신한다.

## 왜 워크플로에서 경로 필터를 뺐나

두 워크플로 모두 `pull_request` 트리거에 `paths:` 필터가 있었다. 이 상태에서 job을 required status check으로 지정하면 **문서만 바꾼 PR이 영구히 막힌다** — GitHub은 경로 필터로 실행되지 않은 required check을 "대기 중"으로 두고 절대 완료시키지 않기 때문이다.

실제로 이 저장소에서 관측된 상태다.

| PR | 성격 | 체크 |
|---|---|---|
| #34 | backend 변경 | `build-and-test` pass |
| #40 | 문서만 변경 | no checks reported |

그래서 `pull_request` 쪽 `paths:`만 제거했다. `push:` 트리거는 머지 게이트가 아니므로 필터를 유지한다. 비용은 무관한 PR에도 붙는 CI 시간(백엔드 약 1분 10초, ai-service 약 11초)이고, public 저장소라 Actions는 무료다.

**경로 필터를 되살리려면 required check에서 먼저 빼야 한다.** 순서를 바꾸면 모든 PR이 잠긴다.

## 설정값

`Settings → Branches → Branch protection rules`, 패턴 `main`

| 항목 | 값 | 이유 |
|---|---|---|
| Require a pull request before merging | ✅ | main 직접 커밋 금지 (`CLAUDE.md`) |
| ↳ Required approvals | **0** | 1인 프로젝트. 1 이상이면 본인 PR을 스스로 승인할 수 없어 자신이 막힌다 |
| Require status checks to pass | ✅ | DoD 핵심 항목 |
| ↳ Required checks | `build-and-test`<br>`ai-service-test` | job 이름과 정확히 일치해야 한다 |
| ↳ Require branches to be up to date | ⬜ | 1인 개발이라 rebase 부담 대비 이득이 적다 |
| Require conversation resolution | ✅ | 리뷰 코멘트 미해결 머지 방지 |
| Require linear history | ✅ | squash merge를 쓰므로 자연히 충족 |
| Allow force pushes | ⬜ | |
| Allow deletions | ⬜ | |
| Do not allow bypassing (include administrators) | 초기 ⬜ → 안정화 후 ✅ | 켜면 본인도 우회 불가라 게이트가 실질적이 된다. 다만 CI가 깨졌을 때 스스로 풀 수 없어, 운영이 안정된 뒤 켠다 |

## 검증 방법

설정 후 아래를 확인한다.

1. 문서만 바꾼 PR에서 `build-and-test`·`ai-service-test`가 **둘 다 실행되고 통과**하는지 (경로 필터 제거가 의도대로 동작하는지)
2. ArchUnit 규칙을 일부러 어기는 커밋을 올린 PR에서 **머지 버튼이 비활성화**되는지 — 테스트가 도는 것과 머지가 막히는 것은 다른 문제이므로 실제로 막히는지 확인한다
