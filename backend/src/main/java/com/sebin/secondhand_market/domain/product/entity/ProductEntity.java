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

  // 거래 희망 장소(동네). 판매자 자유 입력이며 선택 항목이다.
  // 행정동 마스터·동네 인증과는 무관하다 — 그건 별도 기능이다.
  @Column(length = 50)
  private String location;

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

  // 기본 생성자 — 거래 장소는 선택 항목이라 받지 않는 형태를 함께 둔다
  public ProductEntity(
      String title,
      int price,
      String description,
      ProductCategory productCategory,
      ProductStatus productStatus,
      UserEntity seller
  ) {
    this(title, price, description, productCategory, productStatus, seller, null);
  }

  public ProductEntity(
      String title,
      int price,
      String description,
      ProductCategory productCategory,
      ProductStatus productStatus,
      UserEntity seller,
      String location
  ) {
    this.title = title;
    this.price = price;
    this.description = description;
    this.productCategory = productCategory;
    this.productStatus = productStatus;
    this.seller = seller;
    this.location = location;
  }

  // 수정
  public void update(
      String title,
      int price,
      String description,
      ProductCategory productCategory,
      String location
  ) {
    this.title = title;
    this.price = price;
    this.description = description;
    this.productCategory = productCategory;
    this.location = location;

  }

  // 제품 상태 변경
  public void changeStatus(ProductStatus productStatus) {
    this.productStatus = productStatus;
  }

  /**
   * 등록 전에 올려둔 이미지 URL을 상품에 연결한다. 전달된 순서가 표시 순서가 된다.
   *
   * <p>cascade = ALL이므로 상품 저장 시 함께 저장된다.
   */
  public void addImages(List<String> urls) {
    if (urls == null) {
      return;
    }
    int order = images.size();
    for (String url : urls) {
      images.add(new ProductImageEntity(this, url, order++));
    }
  }

}
