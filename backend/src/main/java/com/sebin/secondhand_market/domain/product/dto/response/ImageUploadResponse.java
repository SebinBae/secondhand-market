package com.sebin.secondhand_market.domain.product.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 상품에 붙지 않은 이미지 업로드 응답.
 *
 * <p>{@link ProductImageUploadResponse}와 달리 productId가 없다 — 아직 상품이 만들어지기 전이다.
 */
@Getter
@AllArgsConstructor
public class ImageUploadResponse {

  private List<String> imageUrls;
}
