package com.sebin.secondhand_market.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sebin.secondhand_market.domain.product.entity.ProductEntity;
import com.sebin.secondhand_market.domain.product.entity.ProductImageEntity;
import com.sebin.secondhand_market.domain.product.type.ProductCategory;
import com.sebin.secondhand_market.domain.product.type.ProductStatus;
import com.sebin.secondhand_market.domain.user.entity.UserEntity;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 등록 전 업로드한 이미지가 상품에 연결되는지 검증한다.
 *
 * <p>AI 등록 흐름(사진 → 초안 → 등록)에서 이미지는 상품보다 먼저 존재하므로, 등록 시점에
 * 순서를 지켜 붙어야 한다. 첫 번째가 대표 이미지가 되기 때문에 순서가 곧 화면 결과다.
 */
class ProductCreateWithImagesTest {

  private ProductEntity newProduct() {
    return new ProductEntity(
        "허먼밀러 세이체어", 185000, "설명",
        ProductCategory.FURNITURE, ProductStatus.SELLING,
        new UserEntity("seller@example.com", "encoded", "민트초코러버"));
  }

  @Test
  @DisplayName("이미지 URL을 전달한 순서대로 displayOrder가 매겨진다")
  void addsImagesInOrder() {
    ProductEntity product = newProduct();

    product.addImages(List.of("https://cdn/a.jpg", "https://cdn/b.jpg", "https://cdn/c.jpg"));

    assertThat(product.getImages())
        .extracting(ProductImageEntity::getUrl, ProductImageEntity::getDisplayOrder)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("https://cdn/a.jpg", 0),
            org.assertj.core.groups.Tuple.tuple("https://cdn/b.jpg", 1),
            org.assertj.core.groups.Tuple.tuple("https://cdn/c.jpg", 2));
  }

  @Test
  @DisplayName("이미지가 없어도 상품은 만들어진다 — 사진 없는 등록 경로를 막지 않는다")
  void createsWithoutImages() {
    ProductEntity product = newProduct();

    product.addImages(null);

    assertThat(product.getImages()).isEmpty();
  }

  @Test
  @DisplayName("이미 이미지가 있으면 뒤에 이어 붙는다")
  void appendsAfterExistingImages() {
    ProductEntity product = newProduct();
    product.addImages(List.of("https://cdn/a.jpg"));

    product.addImages(List.of("https://cdn/b.jpg"));

    assertThat(product.getImages())
        .extracting(ProductImageEntity::getDisplayOrder)
        .containsExactly(0, 1);
  }
}
