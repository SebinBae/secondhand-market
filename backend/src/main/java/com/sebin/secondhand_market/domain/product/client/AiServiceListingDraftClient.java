package com.sebin.secondhand_market.domain.product.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * ai-service HTTP 호출 구현.
 *
 * <p>AI 응답을 기다리느라 등록 화면이 묶이지 않도록 연결 2초 / 응답 15초에서 끊는다. 타임아웃·5xx·계약 불일치는
 * 모두 "AI 미가용"으로 동일하게 처리하되, 원인은 로그로 남긴다.
 */
@Slf4j
@Component
public class AiServiceListingDraftClient implements ListingDraftClient {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
  private static final String DRAFT_PATH = "/internal/v1/listing-drafts";

  private final RestClient restClient;

  @Autowired
  public AiServiceListingDraftClient(@Value("${ai.service.url}") String baseUrl) {
    this(RestClient.builder().requestFactory(timeoutRequestFactory()), baseUrl);
  }

  /** 테스트에서 {@code MockRestServiceServer}로 바인딩한 빌더를 주입하기 위한 생성자. */
  AiServiceListingDraftClient(RestClient.Builder builder, String baseUrl) {
    this.restClient = builder.baseUrl(baseUrl).build();
  }

  private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(READ_TIMEOUT);
    return factory;
  }

  @Override
  public Optional<AiListingDraft> createDraft(List<String> imageUrls, String userHint) {
    try {
      AiListingDraft draft = restClient.post()
          .uri(DRAFT_PATH)
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("imageUrls", imageUrls, "userHint", userHint == null ? "" : userHint))
          .retrieve()
          .body(AiListingDraft.class);

      if (!isComplete(draft)) {
        log.warn("ai-service 응답이 계약과 다르다 - 수동 등록으로 안내한다: {}", draft);
        return Optional.empty();
      }
      return Optional.of(draft);
    } catch (Exception e) {
      // 타임아웃, 5xx, 계약 불일치 모두 여기로 온다. 상품 등록 자체는 막지 않는다.
      log.warn("ai-service 초안 생성 실패 - 수동 등록으로 안내한다", e);
      return Optional.empty();
    }
  }

  /** 응답이 비거나 필수 필드가 빠진 경우를 걸러낸다(HTTP 200이어도 계약 불일치일 수 있다). */
  private boolean isComplete(AiListingDraft draft) {
    return draft != null
        && draft.category() != null
        && StringUtils.hasText(draft.title())
        && StringUtils.hasText(draft.description())
        && draft.suggestedPrice() != null
        && StringUtils.hasText(draft.confidence());
  }
}
