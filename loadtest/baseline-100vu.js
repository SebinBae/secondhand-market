import http from 'k6/http';
import { check, sleep } from 'k6';

// Phase 2 Before/After 측정을 위한 평시 부하 시나리오 (기획서 기준: 평시 100 VU).
// 로그인(setup) → 상품 목록 조회 → 상품 검색. 핵심 관찰 지표는 http_req_duration p95.
// 지금은 도구/시나리오 준비 단계이며, 실제 Before 기준선 측정은 MVP 완성 시점(8/30~31)에 수행한다.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SEARCH_KEYWORD = __ENV.K6_SEARCH_KEYWORD || '노트북';

export const options = {
  scenarios: {
    baseline: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 100 }, // 램프업
        { duration: '5m', target: 100 }, // 유지
        { duration: '1m', target: 0 },   // 램프다운
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    // 가설 수치. Phase 2 Before 측정 이전이라 통과가 목적이 아니라 기준선 기록이 목적.
    // 실패해도 그 자체가 데이터이므로 이 임계값은 참고용이다.
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
    // 요청별 지표를 요약에 남기기 위해 서브메트릭을 선언한다.
    // k6는 threshold로 선언한 서브메트릭만 집계·출력하므로, 태그만 붙여서는 나오지 않는다.
    'http_req_duration{name:products_list}': ['p(95)<300'],
    'http_req_duration{name:products_search}': ['p(95)<300'],
  },
};

// 테스트 계정으로 1회 로그인해 액세스 토큰을 확보한다. 계정 정보는 환경변수로만 주입한다.
export function setup() {
  const email = __ENV.K6_TEST_EMAIL;
  const password = __ENV.K6_TEST_PASSWORD;
  if (!email || !password) {
    throw new Error('환경변수 K6_TEST_EMAIL / K6_TEST_PASSWORD 를 설정하세요.');
  }

  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'auth_login' } }
  );
  check(res, { 'login is 200': (r) => r.status === 200 });

  const token = res.json('accessToken');
  if (!token) {
    throw new Error(`로그인 실패: status=${res.status}, body=${res.body}`);
  }
  return { token };
}

export default function (data) {
  const authParams = {
    headers: { Authorization: `Bearer ${data.token}` },
  };

  // 상품 목록 조회 (keyword 없이 조회)
  const list = http.get(`${BASE_URL}/api/products?page=0&size=10`, {
    ...authParams,
    tags: { name: 'products_list' },
  });
  check(list, { 'list is 200': (r) => r.status === 200 });

  // 상품 검색 (keyword 지정) — Phase 2 개선 대상(QueryDSL LIKE)
  const keyword = encodeURIComponent(SEARCH_KEYWORD);
  const search = http.get(`${BASE_URL}/api/products?keyword=${keyword}&page=0&size=10`, {
    ...authParams,
    tags: { name: 'products_search' },
  });
  check(search, { 'search is 200': (r) => r.status === 200 });

  sleep(1); // think time
}
