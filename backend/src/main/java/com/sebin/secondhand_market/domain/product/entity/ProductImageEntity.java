package com.sebin.secondhand_market.domain.product.entity;

import com.sebin.secondhand_market.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
// product_id 인덱스가 필요한 이유: 목록 조회가 상품마다 대표 이미지를 읽으면서 이 테이블을
// product_id로 조회한다. Postgres는 FK 컬럼에 인덱스를 자동으로 만들지 않고 @OneToMany도
// 만들어 주지 않아서, 없으면 목록 요청마다 product_images 전체를 순차 스캔한다.
@Table(name = "product_images", indexes = {
    @Index(name = "idx_product_images_product_id", columnList = "product_id")
})
public class ProductImageEntity extends BaseEntity {

  @Id
  @GeneratedValue
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id")
  private ProductEntity product;

  @Column(nullable = false)
  private String url;

  // 상품 내 이미지 표시 순서 (0부터)
  @Column(nullable = false)
  private int displayOrder;

  public ProductImageEntity(ProductEntity product, String url, int displayOrder) {
    this.product = product;
    this.url = url;
    this.displayOrder = displayOrder;
  }
}
