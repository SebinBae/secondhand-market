package com.sebin.secondhand_market.domain.product.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sebin.secondhand_market.domain.product.type.ProductCategory;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * ai-service 호출 실패가 예외로 새어 나가지 않고 "AI 미가용"으로 수렴하는지 검증한다.
 * 실제 HTTP 대신 {@link MockRestServiceServer}로 응답을 지정한다.
 */
class AiServiceListingDraftClientTest {

  private static final String BASE_URL = "http://ai-service:8000";
  private static final String DRAFT_URL = BASE_URL + "/internal/v1/listing-drafts";

  private static final String VALID_BODY = """
      {
        "category": "DIGITAL",
        "title": "캐논 EOS 550D DSLR 카메라",
        "description": "외관 상태 양호하며 작동 이상 없습니다.",
        "suggestedPrice": { "amount": 250000, "rationale": "동일 모델 중고 시세를 고려한 금액입니다." },
        "confidence": "HIGH"
      }
      """;

  private MockRestServiceServer server;
  private AiServiceListingDraftClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client = new AiServiceListingDraftClient(builder, BASE_URL);
  }

  private Optional<AiListingDraft> callDraft() {
    return client.createDraft(List.of("https://cdn/a.jpg"), "캐논 DSLR 팝니다");
  }

  @Test
  @DisplayName("정상 응답이면 계약대로 매핑한다")
  void mapsSuccessfulResponse() {
    server.expect(requestTo(DRAFT_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(VALID_BODY, MediaType.APPLICATION_JSON));

    AiListingDraft draft = callDraft().orElseThrow();

    assertThat(draft.category()).isEqualTo(ProductCategory.DIGITAL);
    assertThat(draft.title()).isEqualTo("캐논 EOS 550D DSLR 카메라");
    assertThat(draft.suggestedPrice().amount()).isEqualTo(250000);
    assertThat(draft.confidence()).isEqualTo("HIGH");
    server.verify();
  }

  @Test
  @DisplayName("응답 타임아웃이면 예외를 던지지 않고 비어 있는 결과를 준다")
  void returnsEmptyOnTimeout() {
    server.expect(requestTo(DRAFT_URL))
        .andRespond(withException(new SocketTimeoutException("read timed out")));

    assertThat(callDraft()).isEmpty();
  }

  @Test
  @DisplayName("ai-service가 5xx면 비어 있는 결과를 준다")
  void returnsEmptyOnServerError() {
    server.expect(requestTo(DRAFT_URL)).andRespond(withServerError());

    assertThat(callDraft()).isEmpty();
  }

  @Test
  @DisplayName("계약에 없는 카테고리를 주면 비어 있는 결과를 준다")
  void returnsEmptyOnUnknownCategory() {
    server.expect(requestTo(DRAFT_URL))
        .andRespond(withSuccess(
            VALID_BODY.replace("\"DIGITAL\"", "\"SPACESHIP\""), MediaType.APPLICATION_JSON));

    assertThat(callDraft()).isEmpty();
  }

  @Test
  @DisplayName("200이어도 필수 필드가 빠지면 비어 있는 결과를 준다")
  void returnsEmptyOnIncompleteBody() {
    server.expect(requestTo(DRAFT_URL))
        .andRespond(withSuccess("{\"category\": \"DIGITAL\"}", MediaType.APPLICATION_JSON));

    assertThat(callDraft()).isEmpty();
  }
}
