package com.sebin.secondhand_market.global.config;

import com.sebin.secondhand_market.domain.chat.websocket.ChatSubscriptionInterceptor;
import com.sebin.secondhand_market.global.security.websocket.JwtChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@EnableWebSocketMessageBroker
@Configuration
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final JwtChannelInterceptor jwtChannelInterceptor;
  private final ChatSubscriptionInterceptor chatSubscriptionInterceptor;

  // client ---> server(Handshake URL)
  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    // 핸드셰이크에는 인증 인터셉터를 붙이지 않는다.
    // 브라우저의 WebSocket API는 핸드셰이크 요청에 Authorization 헤더를 넣을 수 없다.
    // 인증은 STOMP CONNECT 프레임에서 한다 — JwtChannelInterceptor 참조.
    registry.addEndpoint("/ws-chat")
        .setAllowedOriginPatterns("http://localhost:3000", "http://localhost:5173");
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    // 클라이언트가 구독 경로 -> simpleBroker 가 구독한 모든 클라이언트에게 전달
    registry.enableSimpleBroker("/topic");

    // client -> server 메시지를 SEND 하면 @MessageMapping 메소드로 전달됨.
    registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    // 순서가 중요하다 — 인증(principal 주입)이 먼저 끝나야 구독 인가가 판단할 수 있다.
    registration.interceptors(jwtChannelInterceptor, chatSubscriptionInterceptor);
  }
}
