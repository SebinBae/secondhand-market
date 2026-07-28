package com.sebin.secondhand_market.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sebin.secondhand_market.domain.chat.dto.request.ChatMessageSendRequest;
import com.sebin.secondhand_market.domain.chat.dto.response.ChatMessageResponse;
import com.sebin.secondhand_market.domain.chat.entity.ChatRoomEntity;
import com.sebin.secondhand_market.domain.chat.repository.ChatRoomRepository;
import com.sebin.secondhand_market.domain.product.entity.ProductEntity;
import com.sebin.secondhand_market.domain.product.repository.ProductRepository;
import com.sebin.secondhand_market.domain.product.type.ProductCategory;
import com.sebin.secondhand_market.domain.product.type.ProductStatus;
import com.sebin.secondhand_market.domain.user.entity.UserEntity;
import com.sebin.secondhand_market.domain.user.repository.UserRepository;
import com.sebin.secondhand_market.global.security.JwtProvider;
import com.sebin.secondhand_market.global.websocket.StompAppDestination;
import com.sebin.secondhand_market.global.websocket.StompDestination;
import com.sebin.secondhand_market.global.websocket.WebSocketEndpoint;
import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * 브라우저와 같은 조건에서의 STOMP 인증·인가 검증.
 *
 * <p>핵심은 <b>핸드셰이크에 Authorization 헤더를 넣지 않는다</b>는 점이다. 브라우저의 WebSocket API로는
 * 그 헤더를 넣을 수 없으므로, 기존 {@code ChatWebSocketTest}처럼 핸드셰이크 헤더에 의존하는 검증만으로는
 * 실제 프론트에서 동작하는지 알 수 없다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ChatWebSocketAuthTest {

  @Autowired
  ChatRoomRepository chatRoomRepository;
  @Autowired
  UserRepository userRepository;
  @Autowired
  ProductRepository productRepository;
  @Autowired
  JwtProvider jwtProvider;

  @LocalServerPort
  int port;

  WebSocketStompClient stompClient;

  UUID roomId;
  UUID sellerId;
  UUID outsiderId;

  @BeforeEach
  void setUp() {
    stompClient = createStompClient();

    UserEntity seller = userRepository.save(newUser("판매자"));
    UserEntity buyer = userRepository.save(newUser("구매자"));
    UserEntity outsider = userRepository.save(newUser("제3자"));
    sellerId = seller.getId();
    outsiderId = outsider.getId();

    ProductEntity product = productRepository.save(new ProductEntity(
        "아이폰 21", 10000000, "급전이 필요하여 판매합니다.",
        ProductCategory.DIGITAL, ProductStatus.SELLING, seller));

    roomId = chatRoomRepository.save(new ChatRoomEntity(product, seller, buyer)).getId();
  }

  private UserEntity newUser(String nickname) {
    return new UserEntity(UUID.randomUUID() + "@example.com", "encoded", nickname);
  }

  /** 브라우저와 동일하게 CONNECT 헤더로만 인증한다. 핸드셰이크에는 아무것도 싣지 않는다. */
  private StompSession connectLikeBrowser(String token) throws Exception {
    StompHeaders connectHeaders = new StompHeaders();
    if (token != null) {
      connectHeaders.add("Authorization", "Bearer " + token);
    }

    return stompClient.connectAsync(
        "ws://localhost:" + port + WebSocketEndpoint.CHAT,
        new WebSocketHttpHeaders(), // 비어 있음 — 브라우저는 여기에 헤더를 넣을 수 없다
        connectHeaders,
        new StompSessionHandlerAdapter() {
        }
    ).get(3, TimeUnit.SECONDS);
  }

  @Test
  @DisplayName("핸드셰이크에 헤더 없이 CONNECT 헤더만으로 접속해 구독·송수신할 수 있다")
  void connectsWithConnectHeaderOnly() throws Exception {
    StompSession session = connectLikeBrowser(jwtProvider.createToken(sellerId));

    BlockingQueue<ChatMessageResponse> received = new LinkedBlockingQueue<>();
    session.subscribe(StompDestination.chatRoom(roomId), new StompFrameHandlerFor(received));
    Thread.sleep(200);

    StompHeaders sendHeaders = new StompHeaders();
    sendHeaders.setDestination("/app" + StompAppDestination.CHAT_SEND);
    sendHeaders.setContentType(MimeTypeUtils.APPLICATION_JSON);
    // SEND에는 토큰을 싣지 않는다 — CONNECT에서 인증된 principal이 세션에 유지돼야 한다
    session.send(sendHeaders, new ChatMessageSendRequest(roomId, "hello from browser"));

    ChatMessageResponse response = received.poll(5, TimeUnit.SECONDS);
    assertThat(response).isNotNull();
    assertThat(response.getRoomId()).isEqualTo(roomId);
    assertThat(response.getContent()).isEqualTo("hello from browser");
  }

  @Test
  @DisplayName("토큰 없는 CONNECT는 거부된다")
  void rejectsConnectWithoutToken() {
    assertThatThrownBy(() -> connectLikeBrowser(null))
        .hasMessageContaining("Connection closed");
  }

  @Test
  @DisplayName("유효하지 않은 토큰의 CONNECT는 거부된다")
  void rejectsConnectWithInvalidToken() {
    assertThatThrownBy(() -> connectLikeBrowser("this.is.not.a.jwt"))
        .hasMessageContaining("Connection closed");
  }

  @Test
  @DisplayName("참여자가 아닌 사용자는 채팅방을 구독할 수 없다")
  void rejectsSubscribeByOutsider() throws Exception {
    StompSession session = connectLikeBrowser(jwtProvider.createToken(outsiderId));

    BlockingQueue<ChatMessageResponse> received = new LinkedBlockingQueue<>();
    session.subscribe(StompDestination.chatRoom(roomId), new StompFrameHandlerFor(received));

    // 구독이 거부되면 서버가 세션을 끊는다. 끊긴 세션으로는 아무것도 보낼 수 없다.
    Thread.sleep(500);
    assertThat(session.isConnected()).isFalse();
  }

  private record StompFrameHandlerFor(BlockingQueue<ChatMessageResponse> queue)
      implements org.springframework.messaging.simp.stomp.StompFrameHandler {

    @Override
    public Type getPayloadType(StompHeaders headers) {
      return ChatMessageResponse.class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
      queue.offer((ChatMessageResponse) payload);
    }
  }

  private WebSocketStompClient createStompClient() {
    WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());

    MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
    ObjectMapper om = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
    resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);
    converter.setContentTypeResolver(resolver);
    converter.setObjectMapper(om);
    client.setMessageConverter(converter);

    return client;
  }
}
