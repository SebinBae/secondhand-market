-- 부하테스트용 상품 시드 (10만 건)
--
-- 왜 필요한가: 상품이 수십 건뿐인 DB에서 `%키워드%` 풀스캔은 0.1ms 안에 끝난다.
-- 그 수치를 Before로 기록하면 Phase 2 개선(Elasticsearch 전환)이 개선으로 보일 수 없다.
--
-- 재현성: 난수를 전혀 쓰지 않는다. 모든 값을 일련번호 i에서 결정적으로 유도하므로
-- 다시 실행하면 UUID까지 동일한 데이터셋이 나온다. After 측정 때 같은 조건을 재현하기 위함이다.
--
-- 실행:
--   docker exec -i secondhand-market-db bash -c \
--     'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < loadtest/seed.sql
--
-- 주의: 부하테스트 계정(loadtest@example.com)이 먼저 있어야 한다. loadtest/README.md 참조.

\set ON_ERROR_STOP on

-- 재실행 시 중복을 막는다. 다른 사용자의 상품은 건드리지 않는다.
DELETE FROM products
WHERE seller_id = (SELECT id FROM users WHERE email = 'loadtest@example.com');

INSERT INTO products (
  id, created_at, updated_at, title, price, description,
  product_category, product_status, seller_id
)
SELECT
  -- 일련번호에서 유도한 결정적 UUID
  md5('loadtest-' || i)::uuid,
  TIMESTAMP '2026-01-01 00:00:00' + (i * INTERVAL '1 minute'),
  TIMESTAMP '2026-01-01 00:00:00' + (i * INTERVAL '1 minute'),
  -- 100건에 1건만 검색 키워드('노트북')에 매칭시킨다.
  -- 적중률이 100%거나 0%면 페이지네이션·정렬 비용이 왜곡된다.
  CASE WHEN i % 100 = 0
    THEN (ARRAY['삼성','LG','애플','레노버','ASUS'])[(i / 100) % 5 + 1] || ' 중고 노트북 ' || i || '호'
    ELSE (ARRAY['원목 책상','가죽 소파','겨울 패딩','런닝화','소설책','전공 서적',
                '스테인리스 팬','전기포트','블루투스 이어폰','모니터'])[i % 10 + 1] || ' ' || i || '호'
  END,
  10000 + (i % 90) * 1000,
  '부하테스트용 시드 데이터입니다. 일련번호 ' || i || '. 실제 매물이 아닙니다.',
  (ARRAY['DIGITAL','FURNITURE','CLOTHING','BOOK','KITCHEN_UTENSIL'])[i % 5 + 1],
  -- 검색은 status 미지정 시 SELLING으로 필터된다(ProductSearchRequest.getResolvedStatus).
  -- 전량 SELLING이면 필터가 무의미해지므로 일부를 SOLD로 둔다.
  CASE WHEN i % 7 = 0 THEN 'SOLD' ELSE 'SELLING' END,
  (SELECT id FROM users WHERE email = 'loadtest@example.com')
FROM generate_series(1, 100000) AS i;

-- 실제로 만들어진 분포를 확인한다. 측정 문서에 이 수치를 그대로 기록한다.
SELECT
  count(*)                                                              AS total,
  count(*) FILTER (WHERE product_status = 'SELLING')                    AS selling,
  count(*) FILTER (WHERE lower(title) LIKE '%노트북%')                   AS keyword_match,
  count(*) FILTER (WHERE lower(title) LIKE '%노트북%'
                     AND product_status = 'SELLING')                    AS keyword_match_selling
FROM products;
