package com.sebin.secondhand_market.domain.product.entity;

import com.sebin.secondhand_market.domain.product.type.ProductCategory;
import com.sebin.secondhand_market.domain.product.type.ProductStatus;
import com.sebin.secondhand_market.domain.user.entity.UserEntity;
import com.sebin.secondhand_market.global.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "products")
public class ProductEntity extends BaseEntity {

  @Id
  @GeneratedValue
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "seller_id")
  private UserEntity seller;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(nullable = false)
  private int price;

  @Column(nullable = false)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProductCategory productCategory;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProductStatus productStatus;

  // 상품 이미지 (표시 순서 정렬). 상품 삭제 시 함께 제거된다.
  // 목록 조회가 상품마다 썸네일을 읽으므로 BatchSize로 묶는다 — 없으면 페이지 크기만큼 N+1이 난다.
  @BatchSize(size = 100)
  @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("displayOrder asc")
  private List<ProductImageEntity> images = new ArrayList<>();

  /** 대표 이미지 URL. 이미지가 없으면 null. {@code @OrderBy}로 displayOrder 오름차순이 보장된다. */
  public String getThumbnailUrl() {
    return images.isEmpty() ? null : images.get(0).getUrl();
  }

  // 기본 생성자
  public ProductEntity(
      String title,
      int price,
      String description,
      ProductCategory productCategory,
      ProductStatus productStatus,
      UserEntity seller
  ) {
    this.title = title;
    this.price = price;
    this.description = description;
    this.productCategory = productCategory;
    this.productStatus = productStatus;
    this.seller = seller;
  }

  // 수정
  public void update(
      String title,
      int price,
      String description,
      ProductCategory productCategory
  ) {
    this.title = title;
    this.price = price;
    this.description = description;
    this.productCategory = productCategory;

  }

  // 제품 상태 변경
  public void changeStatus(ProductStatus productStatus) {
    this.productStatus = productStatus;
  }

}
