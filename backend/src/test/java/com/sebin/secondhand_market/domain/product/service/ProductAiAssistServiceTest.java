package com.sebin.secondhand_market.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.sebin.secondhand_market.domain.product.client.AiListingDraft;
import com.sebin.secondhand_market.domain.product.client.ListingDraftClient;
import com.sebin.secondhand_market.domain.product.dto.request.ListingDraftRequest;
import com.sebin.secondhand_market.domain.product.dto.response.ListingDraftResponse;
import com.sebin.secondhand_market.domain.product.type.ProductCategory;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * AI가 보조 기능이라는 원칙 검증: ai-service가 응답하지 못해도 예외 없이 aiAvailable=false로 응답한다.
 */
@ExtendWith(MockitoExtension.class)
class ProductAiAssistServiceTest {

  @Mock
  private ListingDraftClient listingDraftClient;

  @InjectMocks
  private ProductAiAssistService productAiAssistService;

  private ListingDraftRequest request() {
    ListingDraftRequest request = new ListingDraftRequest();
    ReflectionTestUtils.setField(request, "imageUrls", java.util.List.of("https://cdn/a.jpg"));
    ReflectionTestUtils.setField(request, "userHint", "캐논 DSLR 팝니다");
    return request;
  }

  @Test
  @DisplayName("초안을 받으면 aiAvailable=true와 함께 계약 필드를 그대로 전달한다")
  void returnsDraftWhenAvailable() {
    given(listingDraftClient.createDraft(anyList(), any())).willReturn(Optional.of(
        new AiListingDraft(
            ProductCategory.DIGITAL,
            "캐논 EOS 550D DSLR 카메라",
            "외관 상태 양호합니다.",
            new AiListingDraft.SuggestedPrice(250000, "동일 모델 중고 시세 기준"),
            "HIGH")));

    ListingDraftResponse response = productAiAssistService.createDraft(request());

    assertThat(response.aiAvailable()).isTrue();
    assertThat(response.category()).isEqualTo(ProductCategory.DIGITAL);
    assertThat(response.suggestedPrice().amount()).isEqualTo(250000);
  }

  @Test
  @DisplayName("ai-service를 쓸 수 없으면 예외 대신 aiAvailable=false로 응답한다")
  void returnsUnavailableWhenClientFails() {
    given(listingDraftClient.createDraft(anyList(), any())).willReturn(Optional.empty());

    ListingDraftResponse response = productAiAssistService.createDraft(request());

    assertThat(response.aiAvailable()).isFalse();
    assertThat(response.category()).isNull();
    assertThat(response.title()).isNull();
    assertThat(response.suggestedPrice()).isNull();
  }
}
