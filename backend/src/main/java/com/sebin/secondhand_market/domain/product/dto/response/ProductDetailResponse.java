package com.sebin.secondhand_market.domain.product.dto.response;

import com.sebin.secondhand_market.domain.product.entity.ProductEntity;
import com.sebin.secondhand_market.domain.product.entity.ProductImageEntity;
import com.sebin.secondhand_market.domain.product.type.ProductCategory;
import com.sebin.secondhand_market.domain.product.type.ProductStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductDetailResponse {

  private UUID id;
  private UUID sellerId;
  private String sellerNickname;
  private String title;
  private int price;
  private String description;
  private ProductStatus productStatus;
  private ProductCategory productCategory;
  private List<String> imageUrls;
  private LocalDateTime createdAt;

  public static ProductDetailResponse from(ProductEntity product) {
    List<String> imageUrls = product.getImages().stream()
        .map(ProductImageEntity::getUrl)
        .toList();

    return new ProductDetailResponse(
        product.getId(),
        product.getSeller().getId(),
        // 상세는 단건이라 판매자 프록시 초기화 1회로 끝난다. 목록에는 넣지 않는다.
        product.getSeller().getNickname(),
        product.getTitle(),
        product.getPrice(),
        product.getDescription(),
        product.getProductStatus(),
        product.getProductCategory(),
        imageUrls,
        product.getCreatedAt()
    );
  }
}
