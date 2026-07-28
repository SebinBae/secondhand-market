package com.sebin.secondhand_market.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sebin.secondhand_market.domain.product.dto.response.ProductDetailResponse;
import com.sebin.secondhand_market.domain.product.dto.response.ProductResponse;
import com.sebin.secondhand_market.domain.product.entity.ProductEntity;
import com.sebin.secondhand_market.domain.product.type.ProductCategory;
import com.sebin.secondhand_market.domain.product.type.ProductStatus;
import com.sebin.secondhand_market.domain.user.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 예약중(RESERVED) 상태와 거래 장소 필드 검증.
 *
 * <p>화면 02/03은 판매중·예약중·판매완료 세 상태를 칩으로 자유롭게 오간다. 그래서 상태 전이 제약은
 * 두지 않았고, 어떤 상태로도 바뀔 수 있어야 한다는 것이 여기서 확인하려는 내용이다.
 */
class ProductStatusAndLocationTest {

  private ProductEntity newProduct(String location) {
    return new ProductEntity(
        "허먼밀러 세이체어", 185000, "설명",
        ProductCategory.FURNITURE, ProductStatus.SELLING,
        new UserEntity("seller@example.com", "encoded", "민트초코러버"),
        location);
  }

  @ParameterizedTest
  @EnumSource(ProductStatus.class)
  @DisplayName("어떤 상태로도 변경할 수 있다 — 전이 제약을 두지 않았다")
  void changesToAnyStatus(ProductStatus target) {
    ProductEntity product = newProduct("성수동");

    product.changeStatus(target);

    assertThat(product.getProductStatus()).isEqualTo(target);
  }

  @Test
  @DisplayName("ProductStatus는 판매중·예약중·판매완료 세 가지다")
  void hasThreeStatuses() {
    assertThat(ProductStatus.values())
        .containsExactly(ProductStatus.SELLING, ProductStatus.RESERVED, ProductStatus.SOLD);
  }

  @Test
  @DisplayName("거래 장소가 목록·상세 응답에 모두 실린다")
  void locationIsCarriedInResponses() {
    ProductEntity product = newProduct("성수동");

    assertThat(ProductResponse.from(product).getLocation()).isEqualTo("성수동");
    assertThat(ProductDetailResponse.from(product).getLocation()).isEqualTo("성수동");
  }

  @Test
  @DisplayName("거래 장소는 선택 항목이라 없어도 등록된다")
  void locationIsOptional() {
    ProductEntity product = newProduct(null);

    assertThat(product.getLocation()).isNull();
    assertThat(ProductResponse.from(product).getLocation()).isNull();
  }

  @Test
  @DisplayName("수정으로 거래 장소를 바꿀 수 있다")
  void updatesLocation() {
    ProductEntity product = newProduct("성수동");

    product.update("허먼밀러 세이체어", 175000, "설명", ProductCategory.FURNITURE, "합정동");

    assertThat(product.getLocation()).isEqualTo("합정동");
  }
}
