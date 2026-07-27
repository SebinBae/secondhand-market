# loadtest — k6 부하 테스트

Phase 2 Before/After 성능 측정을 위한 도구/시나리오.

**Before 기준선 측정 완료 (2026-07-27).** 결과와 해석은 [`docs/measurements/baseline.md`](../docs/measurements/baseline.md)에 있다. After 측정은 반드시 같은 시드 데이터셋·같은 시나리오로 수행한다.

기획서 기준: 평시 100 VU, 스파이크 300 VU, 핵심 지표는 응답시간 p95.

## 시나리오

| 파일 | 부하 | 용도 |
|------|------|------|
| `smoke.js` | 1 VU, 30초 | 파이프라인 검증 (로그인 → 목록 → 검색이 2xx인지) |
| `baseline-100vu.js` | 0→100 VU (2m 램프업 / 5m 유지 / 1m 램프다운) | 평시 부하 기준선 측정 |

두 시나리오 모두 대상 API는 동일하다:
- 로그인 `POST /api/auth/login` — `setup()`에서 1회만 호출해 토큰 확보 후 재사용
- 상품 목록 조회 `GET /api/products?page=0&size=10`
- 상품 검색 `GET /api/products?keyword=<키워드>&page=0&size=10` (Phase 2 개선 대상)

> `http_req_duration: p(95)<300` 임계값은 **가설 수치**다. Before 측정 이전이라 통과가 목적이 아니라 기준선을 기록하는 것이 목적이며, 실패해도 그 자체가 데이터다.

## 사전 조건

1. **k6 설치** — https://k6.io/docs/get-started/installation/
   ```bash
   # 예: Debian/Ubuntu
   sudo gpg -k
   sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
   echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
   sudo apt-get update && sudo apt-get install k6
   ```
2. **애플리케이션 + 인프라 기동** (백엔드는 Redis에 의존)
   ```bash
   cd ../backend
   docker compose up -d      # 앱 + Redis + Prometheus + Grafana
   # 또는 앱만 별도 실행하려면 Redis가 떠 있는 상태에서:
   ./gradlew bootRun
   ```
3. **테스트 계정 생성** — 로그인에 사용할 계정을 미리 만들어 둔다.
   ```bash
   curl -X POST http://localhost:8080/api/auth/signup \
     -H 'Content-Type: application/json' \
     -d '{"email":"loadtest@example.com","password":"<password>","nickname":"부하테스트"}'
   ```
4. **시드 데이터 투입** — `seed.sql`로 상품 10만 건을 넣는다. 위 계정이 판매자가 되므로 3번 다음에 실행한다.
   ```bash
   docker exec -i secondhand-market-db bash -c \
     'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < seed.sql
   ```
   실행이 끝나면 전체/SELLING/키워드 매칭 건수가 출력된다. 재실행해도 같은 데이터셋이 나오고, 다른 사용자의 상품은 건드리지 않는다.

   > **데이터 없이 측정하지 말 것.** 상품이 수십 건인 상태에서 `%키워드%` 풀스캔은 0.1ms 안에 끝난다. 그 수치를 기준선으로 삼으면 Phase 2 개선이 개선으로 보이지 않는다.

## 실행

계정 정보는 **환경변수로만 주입**한다 (스크립트에 하드코딩하지 않는다).

```bash
export K6_TEST_EMAIL="loadtest@example.com"
export K6_TEST_PASSWORD="<password>"

# 스모크 (파이프라인 점검)
k6 run smoke.js

# 평시 100 VU 기준선
k6 run baseline-100vu.js
```

### k6를 설치하지 않고 실행하기 (권장)

apt 설치는 sudo가 필요하다. Docker 이미지를 compose 네트워크에 붙이면 설치 없이 돌릴 수 있고, 2026-07-27 기준선도 이 방식으로 측정했다.

```bash
docker run --rm -i --user "$(id -u)" --network secondhand-market_monitoring \
  -v "$PWD/results:/results" \
  -e BASE_URL=http://app:8080 \
  -e K6_TEST_EMAIL="loadtest@example.com" \
  -e K6_TEST_PASSWORD="<password>" \
  grafana/k6 run --summary-export=/results/$(date +%F)-baseline-100vu.json - < baseline-100vu.js
```

- `BASE_URL`이 `localhost`가 아니라 **컨테이너 이름(`app`)** 이다. 네트워크 이름은 `docker network ls`로 확인한다
- `--user "$(id -u)"`가 없으면 마운트한 `results/`에 쓰지 못해 **요약 파일이 조용히 생성되지 않는다.** k6 이미지는 uid 12345로 도는데 호스트 디렉터리는 내 소유이기 때문이다. `--user root`로도 되지만 결과물이 root 소유로 남아 나중에 지우기 번거롭다

### 선택 환경변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `BASE_URL` | `http://localhost:8080` | 대상 서버. k6를 컨테이너로 돌리면 `http://app:8080` |
| `K6_TEST_EMAIL` | (필수) | 로그인 계정 |
| `K6_TEST_PASSWORD` | (필수) | 로그인 비밀번호 |
| `K6_SEARCH_KEYWORD` | `노트북` | 검색 시나리오 키워드. 시드 데이터가 이 키워드에 약 1% 매칭된다 |

## 결과 저장

측정 결과는 요약 JSON으로 남긴다. 파일명은 `<날짜>-<시나리오>` 규칙을 따른다.

```bash
k6 run --summary-export=results/2026-08-30-baseline-100vu.json baseline-100vu.js
```

Before/After 비교 시 같은 파일명 규칙으로 저장해 나란히 둔다.

**요청별 지표는 태그만 붙여서는 나오지 않는다.** k6는 threshold로 선언한 서브메트릭만 집계하므로, `baseline-100vu.js`의 `thresholds`에 `http_req_duration{name:products_list}` 같은 항목이 있어야 요약과 export에 포함된다. 시나리오를 추가할 때 같은 규칙을 따른다.
