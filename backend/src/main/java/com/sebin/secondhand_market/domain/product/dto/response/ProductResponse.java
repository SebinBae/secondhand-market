package com.sebin.secondhand_market.domain.product.dto.response;

import com.sebin.secondhand_market.domain.product.entity.ProductEntity;
import com.sebin.secondhand_market.domain.product.type.ProductCategory;
import com.sebin.secondhand_market.domain.product.type.ProductStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductResponse {

  /** 목록에서 상세로 이동하는 키. 없으면 목록 화면이 링크를 만들 수 없다. */
  private UUID id;
  private UUID sellerId;
  private String title;
  private int price;
  private String description;
  private ProductStatus productStatus;
  private ProductCategory productCategory;
  private String thumbnailUrl;
  private LocalDateTime createdAt;

  public static ProductResponse from(ProductEntity productEntity){
    return new ProductResponse(
        productEntity.getId(),
        // 판매자는 id만 읽는다 — 프록시를 초기화하지 않아 목록에서 추가 쿼리가 나가지 않는다.
        productEntity.getSeller().getId(),
        productEntity.getTitle(),
        productEntity.getPrice(),
        productEntity.getDescription(),
        productEntity.getProductStatus(),
        productEntity.getProductCategory(),
        productEntity.getThumbnailUrl(),
        productEntity.getCreatedAt()
    );
  }
}
