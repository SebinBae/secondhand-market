package com.sebin.secondhand_market.domain.product.client;

import java.util.List;
import java.util.Optional;

/**
 * AI 등록 초안 생성 호출 경계.
 *
 * <p>AI는 보조 기능이므로, 호출이 실패해도 예외를 위로 던지지 않고 {@link Optional#empty()}를 돌려준다.
 * 대신 실패 사실은 로그로 남기고 응답에 명시적으로 드러낸다(수동 등록은 항상 가능해야 한다).
 */
public interface ListingDraftClient {

  Optional<AiListingDraft> createDraft(List<String> imageUrls, String userHint);
}
