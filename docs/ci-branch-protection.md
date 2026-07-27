# main 브랜치 보호 설정

Phase 1 DoD "ModuleBoundaryTest가 CI에서 머지 게이트로 동작"의 근거 문서.

저장소 설정은 코드로 남길 수 없으므로 **적용한 값을 여기에 기록**한다. 설정을 바꾸면 이 문서도 함께 갱신한다.

## 왜 워크플로에서 경로 필터를 뺐나

두 워크플로 모두 `pull_request` 트리거에 `paths:` 필터가 있었다. 이 상태에서 job을 required status check으로 지정하면 **문서만 바꾼 PR이 영구히 막힌다** — GitHub은 경로 필터로 실행되지 않은 required check을 "대기 중"으로 두고 절대 완료시키지 않기 때문이다.

실제로 이 저장소에서 관측된 상태다.

| PR | 성격 | 체크 |
|---|---|---|
| #34 | backend 변경 | `build-and-test` pass |
| #40 | 문서만 변경 | no checks reported |

그래서 `pull_request` 쪽 `paths:`만 제거했다. `push:` 트리거는 머지 게이트가 아니므로 필터를 유지한다. 비용은 무관한 PR에도 붙는 CI 시간(백엔드 약 1분 10초, ai-service 약 11초)이고, public 저장소라 Actions는 무료다.

**경로 필터를 되살리려면 required check에서 먼저 빼야 한다.** 순서를 바꾸면 모든 PR이 잠긴다.

## 적용 방식 — classic branch protection이 아니라 **Ruleset**

`Settings → Rules → Rulesets`에 룰셋 하나(`main`, id `19792680`, target `branch`)로 적용했다. classic branch protection rule은 쓰지 않는다. 둘을 섞으면 어느 쪽이 이겼는지 추적하기 어렵다.

적용일: 2026-07-27

### 적용된 규칙 (API 응답 기준)

| 규칙 | 파라미터 | 이유 |
|---|---|---|
| `pull_request` | `required_approving_review_count: 0`<br>`required_review_thread_resolution: true`<br>`allowed_merge_methods: [merge, squash, rebase]` | main 직접 커밋 금지(`CLAUDE.md`). 승인 수는 **0** — 1인 프로젝트에서 1 이상이면 본인 PR을 스스로 승인할 수 없어 자신이 막힌다 |
| `required_status_checks` | `build-and-test`<br>`ai-service-test`<br>`strict_...policy: false` | DoD 핵심 항목. 컨텍스트 이름은 워크플로의 job 이름과 정확히 일치해야 한다. `strict`(브랜치 최신화 요구)는 1인 개발이라 rebase 부담 대비 이득이 적어 껐다 |
| `required_linear_history` | — | squash merge를 쓰므로 자연히 충족 |
| `non_fast_forward` | — | 강제 푸시 금지 |
| `deletion` | — | 브랜치 삭제 금지 |

### bypass_actors: 비어 있음 (= 관리자도 우회 불가)

classic 설정의 "Do not allow bypassing"을 켠 것과 같다. 게이트로서는 이쪽이 실질적이지만, **CI가 깨지면 본인도 머지할 수 없어** 룰셋을 일시적으로 `disabled`로 내렸다 올려야 한다. 그 상황이 반복되면 Repository admin을 bypass actor로 추가하는 것을 검토한다.

## 함정 — 룰셋을 만들어도 적용되지 않을 수 있다

처음 생성했을 때 이 룰셋은 **적용되지 않는 상태**였다. 원인이 두 가지였다.

| 필드 | 잘못된 값 | 증상 |
|---|---|---|
| `enforcement` | `disabled` | 규칙이 평가조차 되지 않음 |
| `conditions.ref_name.include` | `[]` (비어 있음) | 대상 브랜치가 없어 어떤 브랜치에도 걸리지 않음 |

**룰셋 이름을 `main`으로 지어도 대상 지정과는 무관하다.** Target branches에서 별도로 지정해야 한다(`Include default branch` 권장 — 기본 브랜치가 바뀌어도 따라간다).

UI만 보면 "규칙이 5개 있는 룰셋"으로 보여서 적용된 것처럼 착각하기 쉽다.

## 적용 여부를 확인하는 방법

룰셋 자체의 상태가 아니라 **브랜치에 실제로 걸리는 규칙**을 조회한다. 이게 판정 기준이다.

```bash
gh api repos/SebinBae/secondhand-market/rules/branches/main --jq '.[].type'
```

기대 출력:
```
deletion
non_fast_forward
pull_request
required_status_checks
required_linear_history
```

**비어 있으면 적용되지 않은 것이다.** 위 함정을 그대로 겪은 상태이므로 `enforcement`와 대상 브랜치를 확인한다.

> 저장소가 `secondhand-market-backend`에서 이름이 바뀌어서, git 리모트 기준으로 `:owner/:repo`를 쓰면 GET은 리다이렉트되지만 **본문이 있는 PUT은 307로 실패한다.** 룰셋을 API로 수정할 때는 위처럼 현재 이름을 명시한다.

## 검증 방법

설정만으로는 부족하다. 아래를 실제로 확인한다.

1. 문서만 바꾼 PR에서 `build-and-test`·`ai-service-test`가 **둘 다 실행되고 통과**하는지 — 경로 필터 제거가 의도대로 동작하는지 (PR #42에서 확인됨)
2. 체크가 끝나기 전/실패했을 때 **머지 버튼이 잠기는지** — 테스트가 도는 것과 머지가 막히는 것은 다른 문제다
3. ArchUnit 규칙을 일부러 어긴 PR에서 머지가 막히는지

## 알려진 불일치

`allowed_merge_methods`에 `merge`와 `rebase`가 함께 허용돼 있으나, `CLAUDE.md`의 컨벤션은 **squash merge**다. `required_linear_history`가 켜져 있어 merge commit은 어차피 거부되지만, **거부되는 이유가 "허용되지 않은 방식"이 아니라 "linear history 위반"으로 표시돼 혼란스럽다.** `squash`만 남기는 편이 컨벤션과 일치한다.
