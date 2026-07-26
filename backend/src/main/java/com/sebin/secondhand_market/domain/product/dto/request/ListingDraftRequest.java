package com.sebin.secondhand_market.domain.product.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ListingDraftRequest {

  /** 이미 업로드된 상품 이미지 URL. 최소 1장 필요하다. */
  @NotEmpty
  private List<String> imageUrls;

  /** 판매자가 남긴 힌트(선택). */
  private String userHint;

}
