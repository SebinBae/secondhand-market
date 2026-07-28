# 수동 DDL

`spring.jpa.hibernate.ddl-auto=update`가 **처리하지 못하는** 스키마 변경을 모아 둔다.

## 왜 필요한가

`update` 모드는 이름 그대로 "추가"만 한다. 컬럼을 새로 만들거나 테이블을 만드는 것은 알아서 하지만, **이미 존재하는 제약을 고치지는 않는다.**

실제로 겪은 사례가 그것이다. `ProductStatus`에 `RESERVED`를 추가했더니 애플리케이션과 테스트는 전부 통과했는데, 기존 개발 DB에서는 상태 변경이 이렇게 실패했다.

```
ERROR: new row for relation "products" violates check constraint "products_product_status_check"
DETAIL: Failing row contains (..., RESERVED, ...)
```

Hibernate가 `@Enumerated(EnumType.STRING)` 컬럼에 만들어 둔 CHECK 제약이 `('SELLING','SOLD')` 그대로 남아 있었기 때문이다.

**CI에서는 잡히지 않는다.** 테스트는 매번 빈 H2에 스키마를 새로 만들므로 항상 최신 제약이 생긴다. 기존 데이터가 있는 DB에서만 터진다 — 즉 개발 DB와 운영 DB에서만.

## 적용 방법

```bash
docker exec -i secondhand-market-db bash -c \
  'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < backend/db/2026-07-28-product-status-reserved.sql
```

파일명 앞의 날짜 순서대로 적용한다. 이미 적용된 파일을 다시 실행해도 안전하도록 작성한다(멱등).

**새로 만드는 DB에는 적용할 필요가 없다.** Hibernate가 처음부터 올바른 제약을 만든다. 이 디렉터리는 이미 존재하는 DB를 따라잡게 하는 용도다.

## 적용 여부 확인

```bash
docker exec secondhand-market-db bash -c \
  'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\d products"' \
  | grep product_status_check
```

## 앞으로

enum에 값을 추가할 때마다 이 문제가 반복된다. 파일이 서너 개 쌓이거나 배포 환경이 생기는 시점에는 Flyway 도입을 검토한다 — 지금은 문제 하나를 정확히 막는 것으로 충분하다고 판단했다.

| 파일 | 내용 |
|---|---|
| `2026-07-28-product-status-reserved.sql` | `products.product_status` CHECK 제약에 `RESERVED` 추가 |
