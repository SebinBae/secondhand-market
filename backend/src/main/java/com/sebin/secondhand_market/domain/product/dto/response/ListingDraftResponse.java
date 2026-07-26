package com.sebin.secondhand_market.domain.product.dto.response;

import com.sebin.secondhand_market.domain.product.client.AiListingDraft;
import com.sebin.secondhand_market.domain.product.type.ProductCategory;

/**
 * AI 등록 초안 응답.
 *
 * <p>{@code aiAvailable}이 false면 나머지 필드는 모두 null이다. 프론트는 이 값을 보고 수동 등록으로 안내한다.
 */
public record ListingDraftResponse(
    boolean aiAvailable,
    ProductCategory category,
    String title,
    String description,
    SuggestedPrice suggestedPrice,
    String confidence
) {

  public record SuggestedPrice(int amount, String rationale) {

  }

  public static ListingDraftResponse from(AiListingDraft draft) {
    return new ListingDraftResponse(
        true,
        draft.category(),
        draft.title(),
        draft.description(),
        new SuggestedPrice(draft.suggestedPrice().amount(), draft.suggestedPrice().rationale()),
        draft.confidence()
    );
  }

  public static ListingDraftResponse unavailable() {
    return new ListingDraftResponse(false, null, null, null, null, null);
  }
}
