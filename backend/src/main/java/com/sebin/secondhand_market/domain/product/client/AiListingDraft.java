package com.sebin.secondhand_market.domain.product.client;

import com.sebin.secondhand_market.domain.product.type.ProductCategory;

/**
 * ai-service의 {@code POST /internal/v1/listing-drafts} 응답 계약.
 *
 * <p>필드가 하나라도 계약과 어긋나면 역직렬화 단계에서 실패하고, 호출부는 이를 AI 미가용으로 처리한다.
 */
public record AiListingDraft(
    ProductCategory category,
    String title,
    String description,
    SuggestedPrice suggestedPrice,
    String confidence
) {

  public record SuggestedPrice(int amount, String rationale) {

  }
}
