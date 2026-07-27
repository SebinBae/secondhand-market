# 검색·상품 조회 성능 기준선 (Before)

Phase 2 개선(Redis 캐시, Elasticsearch 전환)의 비교 대상이 되는 Before 수치.

- 측정일: 2026-07-27
- 대상 커밋: main `cb74ab9` (**애플리케이션 코드 변경 없음** — 측정만 수행)
- 시나리오: `loadtest/baseline-100vu.js`
- 원본 결과: `loadtest/results/2026-07-27-baseline-100vu.json`

이 문서는 **측정 기록**이다. 수치가 나쁘다고 이 시점에 쿼리나 인덱스를 손대지 않는다 — 느린 것이 정상이고, 개선은 Phase 2의 별도 작업이다.

---

## 측정 조건

### 데이터

| 항목 | 값 |
|---|---|
| `products` 전체 | **100,003건** (시드 10만 + 기존 3) |
| `product_status = 'SELLING'` | 85,718건 |
| 제목에 `노트북` 포함 | 1,000건 (약 1%) |
| 위 중 SELLING (= 검색 결과 수) | **858건** |
| `users` | 7명 |

시드 스크립트는 `loadtest/seed.sql`. 난수를 쓰지 않고 일련번호에서 모든 값을 유도하므로 **다시 실행하면 UUID까지 동일한 데이터셋이 재현된다.** After 측정 때 같은 조건을 쓰기 위함이다.

**왜 10만 건인가**: 측정 직전 이 DB의 상품은 3건이었고, 그 상태에서 검색 쿼리는 **0.084ms**에 끝났다. 그 수치를 Before로 기록했다면 Elasticsearch로 바꿔도 개선이 나올 수 없다. 비교쌍이 없는 것보다 잘못된 비교쌍이 더 나쁘다.

### 부하

- 0 → 100 VU 램프업 2분 / 100 VU 유지 5분 / 램프다운 1분 (총 8분)
- 반복당: 상품 목록 조회 1회 + 상품 검색 1회 + think time 1초
- 로그인은 `setup()`에서 1회만 수행하고 토큰 재사용

### 환경

- WSL2 (Ubuntu) / 16 vCPU / 16GB RAM 중 컨테이너 가용 약 7GB
- `docker compose`: 앱 + PostgreSQL + Redis + Prometheus + Grafana + ai-service
- k6는 `grafana/k6` 컨테이너로 compose 네트워크에 붙여 실행 (`BASE_URL=http://app:8080`)

---

## 결과

| 지표 | p95 | median | max |
|---|---|---|---|
| **전체 `http_req_duration`** | **796.5ms** | 509.9ms | 2,451ms |
| `products_list` (목록 조회) | **759.6ms** | 483.5ms | 2,069ms |
| `products_search` (검색) | **820.5ms** | 551.3ms | 2,451ms |

- 총 요청 40,451건 / 처리량 **84.1 req/s** / 반복 20,225회
- **실패율 0.00%** — 느려졌을 뿐 에러는 없다
- 가설 임계값 `p(95)<300ms` **미달** (약 2.7배). 임계값은 가설이었고 미달 자체가 데이터다

### 실행 간 변동

같은 조건으로 2회 실행했다.

| 실행 | 전체 p95 | 총 요청 |
|---|---|---|
| 1회차 | 816.98ms | 39,779 |
| 2회차 (기록 대상) | 796.52ms | 40,451 |

약 2.5% 차이. **After 비교 시 이 폭보다 작은 변화는 개선으로 읽지 않는다.**

> 1회차는 요청별 지표가 집계되지 않아 폐기했다. k6는 threshold로 선언한 서브메트릭만 집계하는데, 태그만 붙어 있고 선언이 없었다(task-04의 누락). 2회차부터 `http_req_duration{name:products_list}` 등을 threshold에 추가해 수집했다.

---

## 왜 느린가 — 실행계획

측정 수치만 남기면 나중에 원인을 대조할 수 없으므로 `EXPLAIN ANALYZE`를 함께 기록한다. `products` 테이블의 인덱스는 **PK(`id`) 하나뿐**이다.

### 검색 (키워드 있음)

```
Limit  (actual time=29.057..29.060 rows=10)
  ->  Sort  (Sort Key: created_at DESC)
        ->  Seq Scan on products  (actual time=0.034..28.933 rows=858)
              Filter: (product_status = 'SELLING' AND lower(title) ~~ '%노트북%')
              Rows Removed by Filter: 99145
Execution Time: 29.087 ms
```

`ProductQueryRepositoryImpl.titleContains()`가 QueryDSL `containsIgnoreCase`를 쓰므로 SQL은 `lower(title) like lower('%노트북%')`가 된다. **선행 와일드카드라 B-tree 인덱스를 탈 수 없다.** 10만 행을 전부 읽고 99,145행을 버린다.

여기에 더해 `search()`는 **content 쿼리와 count 쿼리를 각각 실행한다.** count도 같은 조건으로 풀스캔이다.

```
Aggregate  (actual time=28.387..28.389)
  ->  Seq Scan on products  (Rows Removed by Filter: 99145)
Execution Time: 28.412 ms
```

즉 검색 1회당 DB에서만 **약 57ms**가 든다(단일 실행 기준, 동시성 없음).

### 목록 조회 (키워드 없음)

```
Aggregate  (actual time=10.444..10.445)
  ->  Seq Scan on products  (actual time=0.008..8.196 rows=85718)
        Filter: (product_status = 'SELLING')
        Rows Removed by Filter: 14285
Execution Time: 10.480 ms
```

키워드가 없어도 `product_status` 인덱스가 없어 **역시 풀스캔**이다.

---

## 이 측정에서 드러난 것

### 1. 병목이 LIKE만이 아니다 — Phase 2 계획에 영향

목록 조회 p95(759.6ms)가 검색 p95(820.5ms)와 **7% 차이밖에 나지 않는다.** 키워드 검색을 아예 하지 않는 요청도 거의 같은 수준으로 느리다는 뜻이다.

원인은 위 실행계획에 있다 — 두 경로 모두 `product_status` 필터로 풀스캔을 하고, 둘 다 페이지네이션용 `count(*)`를 별도로 돈다. **LIKE는 검색 경로에만 붙는 추가 비용일 뿐, 공통 비용이 이미 크다.**

따라서 **Elasticsearch로 검색만 옮겨도 목록 조회는 그대로 느릴 가능성이 높다.** Phase 2 작업 순서에서 Redis 캐시가 검색 전환보다 앞에 있는 것은 이 결과와 부합한다. 다만 이 문서는 측정 기록이므로 Phase 2 계획 변경은 별도로 판단한다.

### 2. 실패는 없고 지연만 있다

실패율 0%, 처리량 84 req/s로 안정적이었다. 100 VU에서 무너지는 게 아니라 **꾸준히 느려지는** 형태다. 개선 목표는 가용성이 아니라 지연이다.

---

## 이 수치의 한계 (반드시 함께 읽을 것)

- **k6와 애플리케이션이 같은 머신(WSL2)에서 돈다.** 부하 생성기가 앱·DB와 CPU를 다툰다. 별도 부하 머신에서 측정하면 수치가 달라진다
- **절대 수치는 프로덕션 성능이 아니다.** 이 문서의 유일한 목적은 *같은 조건에서* After와 비교할 기준점을 만드는 것이다. "이 서비스의 검색 p95는 800ms"라는 식으로 인용하면 안 된다
- p95 300ms는 기획서의 **가설**이며 검증된 목표가 아니다
- 단일 시드 데이터셋(10만 건, 적중률 1%) 기준이다. 데이터 규모나 적중률이 바뀌면 재측정해야 한다
- Prometheus·Grafana·ai-service가 같은 호스트에 함께 떠 있는 상태에서 측정했다

## After 측정 시 지켜야 할 것

1. `loadtest/seed.sql`을 다시 실행해 **동일한 데이터셋**을 만든다
2. 같은 시나리오(`baseline-100vu.js`)를 같은 방식(compose 네트워크 내 k6 컨테이너)으로 실행한다
3. 결과를 `loadtest/results/<날짜>-<시나리오>.json`으로 저장하고 이 문서 옆에 비교표를 만든다
4. 실행 간 변동(±2.5%)보다 작은 차이는 개선으로 주장하지 않는다
