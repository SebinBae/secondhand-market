package com.sebin.secondhand_market.domain.product.dto.request;

import com.sebin.secondhand_market.domain.product.type.ProductCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProductCreateRequest {

  @NotBlank
  @Size(max = 100)
  private String title;

  @Min(0)
  private int price;

  @NotBlank
  private String description;

  @NotNull
  private ProductCategory productCategory;

  /**
   * 등록 전에 올려둔 이미지 URL. 표시 순서대로이며 첫 번째가 대표 이미지가 된다.
   *
   * <p>선택 항목이다 — 사진 없이 등록하는 경로를 막지 않는다.
   */
  @Size(max = 10)
  private List<String> imageUrls;

  /** 거래 희망 장소(동네). 선택 항목이며 자유 입력이다. */
  @Size(max = 50)
  private String location;

}
