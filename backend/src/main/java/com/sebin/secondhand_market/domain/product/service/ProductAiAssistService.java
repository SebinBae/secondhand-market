package com.sebin.secondhand_market.domain.product.service;

import com.sebin.secondhand_market.domain.product.client.ListingDraftClient;
import com.sebin.secondhand_market.domain.product.dto.request.ListingDraftRequest;
import com.sebin.secondhand_market.domain.product.dto.response.ListingDraftResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI 등록 초안 조회.
 *
 * <p>초안은 제안일 뿐이므로 아무것도 저장하지 않는다. 사용자가 수정한 뒤 기존 등록 API로 저장한다.
 * ai-service가 죽어도 이 API는 200과 {@code aiAvailable=false}로 응답해 수동 등록 흐름을 막지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ProductAiAssistService {

  private final ListingDraftClient listingDraftClient;

  public ListingDraftResponse createDraft(ListingDraftRequest request) {
    return listingDraftClient.createDraft(request.getImageUrls(), request.getUserHint())
        .map(ListingDraftResponse::from)
        .orElseGet(ListingDraftResponse::unavailable);
  }
}
