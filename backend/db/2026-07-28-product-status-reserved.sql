-- products.product_status CHECK 제약에 RESERVED 추가
--
-- ProductStatus에 RESERVED가 생겼지만 ddl-auto=update는 기존 CHECK 제약을 고치지 않는다.
-- 그래서 이미 존재하던 DB에서는 예약중으로의 상태 변경이 제약 위반으로 거부된다.
--
-- 새로 만드는 DB에는 필요 없다 — Hibernate가 처음부터 세 값을 넣어 만든다.
--
-- 실행:
--   docker exec -i secondhand-market-db bash -c \
--     'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
--     < backend/db/2026-07-28-product-status-reserved.sql

\set ON_ERROR_STOP on

-- 제약 이름은 Hibernate가 생성한 것이다. 없으면 그냥 넘어간다(새 DB이거나 이미 적용됨).
ALTER TABLE products DROP CONSTRAINT IF EXISTS products_product_status_check;

ALTER TABLE products ADD CONSTRAINT products_product_status_check
  CHECK (product_status IN ('SELLING', 'RESERVED', 'SOLD'));

-- 적용 결과 확인
SELECT pg_get_constraintdef(oid) AS product_status_check
FROM pg_constraint
WHERE conrelid = 'products'::regclass
  AND conname = 'products_product_status_check';
