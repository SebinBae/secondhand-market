# task-13: k6 Before 기준선 측정

- 브랜치: `chore/k6-baseline-measurement`
- 커밋 타입: `chore:`
- 선행 조건: 없음 (task-04에서 스크립트는 이미 준비됨)

## 배경

Phase 1 DoD의 "k6 Before 기준선 측정 완료 — 검색·상품 조회 p95를 `docs/measurements/baseline.md`에 기록" 항목이 비어 있다.

task-04는 **도구 준비**가 범위였다(DoD: 스크립트가 돌고 p95가 출력됨). 실제 측정은 하지 않았고 `loadtest/results/`는 비어 있다. Phase 2 전체가 이 비교쌍에 걸려 있으므로 — Redis 캐시, Elasticsearch 전환 모두 After를 이 수치와 비교한다 — 먼저 닫는다.

측정 대상인 검색은 `ProductQueryRepositoryImpl.titleContains()`의 `containsIgnoreCase`로, SQL은 `lower(title) like lower('%키워드%')`가 된다. **선행 와일드카드라 인덱스를 탈 수 없고 매 요청 풀스캔이다.** Phase 2에서 Elasticsearch로 바꾸려는 바로 그 지점이다.

## 이 작업의 성패를 가르는 것 — 데이터 볼륨

**데이터가 없으면 측정이 무의미하다.** 상품이 100건뿐인 DB에서 `%키워드%` 풀스캔은 1ms 안에 끝난다. 그 수치를 Before로 기록하면 Elasticsearch 전환 후 "개선되지 않았다" 또는 "오히려 느려졌다"는 결론밖에 안 나온다. 비교쌍이 사라지는 게 아니라 **처음부터 잘못된 비교쌍을 만드는 것**이라 더 나쁘다.

따라서 이 작업의 절반은 측정이 아니라 **재현 가능한 데이터셋을 만드는 일**이다.

### 결정 (이 방향으로 고정)

- **상품 10만 건**을 시드한다. 풀스캔 비용이 드러나면서 로컬에서 다룰 수 있는 규모다
- **검색 키워드 적중률을 고정한다.** `노트북`이 전체의 약 1%(1,000건)에 매칭되도록 제목을 구성한다. 적중률이 100%거나 0%면 페이지네이션·정렬 비용이 왜곡된다
- **SQL 스크립트로 직접 넣는다** (`loadtest/seed.sql`, Postgres `generate_series`). 앱 코드에 시드 로직을 넣지 않는다 — 측정용 코드가 프로덕션 경로에 남는 걸 피한다
- **결정적이어야 한다.** 난수를 쓰더라도 `setseed`로 고정해, After 측정 때 같은 데이터셋을 재현할 수 있어야 한다
- 시드 스크립트를 **커밋한다.** 데이터 자체는 커밋하지 않는다

`products` 테이블 컬럼은 `ProductEntity`에서 확인한다(`seller_id` FK, `title` 100자, `price`, `description`, `product_category`, `product_status`, `BaseEntity`의 생성/수정 시각). `seller_id`는 사전에 만든 부하테스트 계정의 id를 쓴다.

## k6 실행 방법

로컬에 k6가 설치돼 있지 않다(`which k6` 결과 없음). 둘 중 편한 쪽을 쓴다.

- **Docker 이미지** (권장, sudo 불필요): `grafana/k6` 이미지를 compose 네트워크에 붙여 실행. 결과 파일을 얻으려면 `loadtest/`를 볼륨 마운트한다
- 로컬 설치: `loadtest/README.md`의 apt 절차 (sudo 필요 → 사용자가 직접 실행)

Docker로 실행하면 `BASE_URL`이 `http://localhost:8080`이 아니라 컨테이너 이름(`http://app:8080`)이 된다. README의 환경변수 표에 이 경우를 추가한다.

## 작업 단계

1. 스택 기동(`docker compose up -d`) → 부하테스트 계정 생성 → 스모크(`smoke.js`)로 파이프라인이 2xx인지 확인
2. `loadtest/seed.sql` 작성 → 10만 건 시드 → **실제 행 수와 키워드 매칭 건수를 쿼리로 확인**하고 기록
3. 시드 **전후로 `EXPLAIN ANALYZE`**를 떠서 풀스캔임을 실행계획으로 확인 (수치가 아니라 원인을 남기기 위함)
4. `baseline-100vu.js` 실행 → `loadtest/results/<날짜>-baseline-100vu.json`으로 저장
5. `docs/measurements/baseline.md` 작성
6. `loadtest/README.md`에서 "지금은 도구 준비 단계" 문구를 실제 측정 완료 상태로 갱신

## `docs/measurements/baseline.md`에 반드시 들어갈 것

- **측정 조건**: 상품 행 수, 키워드 매칭 건수, 대상 커밋, 실행 일시, k6 시나리오
- **지표**: 태그별(`products_list` / `products_search`) p95 + 전체 `http_req_duration` p95, 실패율
- **실행계획**: 검색 쿼리가 인덱스를 타지 않는다는 `EXPLAIN ANALYZE` 근거
- **한계 명시** — 아래는 반드시 적는다:
  - k6와 애플리케이션이 **같은 머신(WSL2)** 에서 돈다. 부하 생성기가 앱과 자원을 다툰다
  - 따라서 **절대 수치는 프로덕션 성능이 아니다.** 이 문서의 목적은 같은 조건에서 After와 비교할 기준점을 만드는 것뿐이다
  - p95 임계값 300ms는 **가설**이며, 미달해도 실패가 아니라 데이터다

## 하지 말 것

- 수치를 좋게 만들려고 인덱스를 추가하거나 쿼리를 고치지 말 것. **이번은 Before 측정이고, 느린 것이 정상이다**
- Redis·Elasticsearch 도입 금지 (Phase 2 범위)
- 앱 코드에 시드/측정용 코드를 넣지 말 것
- 데이터가 적어서 수치가 안 나온다고 시나리오(VU 수, 지속 시간)를 줄이지 말 것 — 데이터를 늘려서 해결한다
- 측정 결과가 예상과 달라도 해석을 맞추지 말 것. 예상과 다르면 그대로 적고 가설을 남긴다

## 완료 조건 (DoD)

- [ ] `loadtest/seed.sql`이 커밋되고, 다시 실행하면 같은 데이터셋이 재현된다
- [ ] 상품 10만 건 / 키워드 매칭 약 1%가 쿼리 결과로 확인되고 문서에 기록됐다
- [ ] 검색 쿼리가 인덱스를 타지 않음이 `EXPLAIN ANALYZE`로 확인되고 기록됐다
- [ ] `baseline-100vu.js` 실행 결과가 `loadtest/results/`에 저장됐다
- [ ] `docs/measurements/baseline.md`에 `products_list` / `products_search` p95가 각각 기록됐다
- [ ] 측정의 한계(같은 머신, 절대 수치 아님)가 문서에 명시됐다
- [ ] `loadtest/README.md`의 "도구 준비 단계" 문구가 갱신됐다
- [ ] 앱 코드(`backend/src/main`) 변경 0줄

## 이 작업으로 닫히는 것

Phase 1 DoD 4개 중 1개(k6 Before 기준선). 남는 것은 코어 루프 배포 동작(React 미착수), ArchUnit 머지 게이트(main 브랜치 보호 미설정), `v0.1.0` 릴리스다.
