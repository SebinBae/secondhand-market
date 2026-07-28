package com.sebin.secondhand_market.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 브라우저 preflight 검증.
 *
 * <p>상품 상태 변경은 PATCH인데 CORS 허용 메서드 목록에 PATCH가 없었다. 서버 로직이 멀쩡해도
 * 브라우저는 preflight 단계에서 막히므로, 컨트롤러 테스트로는 이 문제가 드러나지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsPreflightTest {

  @Autowired
  private MockMvc mockMvc;

  @ParameterizedTest
  @ValueSource(strings = {"http://localhost:3000", "http://localhost:5173"})
  @DisplayName("허용된 origin에서 PATCH preflight가 통과한다")
  void allowsPatchPreflight(String origin) throws Exception {
    mockMvc.perform(options("/api/products/" + UUID.randomUUID() + "/status")
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "PATCH"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", origin))
        .andExpect(header().stringValues("Access-Control-Allow-Methods",
            org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("PATCH"))));
  }

  @Test
  @DisplayName("허용되지 않은 origin은 preflight에서 거부된다")
  void rejectsUnknownOrigin() throws Exception {
    mockMvc.perform(options("/api/products/" + UUID.randomUUID() + "/status")
            .header("Origin", "http://evil.example.com")
            .header("Access-Control-Request-Method", "PATCH"))
        .andExpect(status().isForbidden());
  }
}
