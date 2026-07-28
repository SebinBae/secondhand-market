package com.sebin.secondhand_market.global.security.websocket;

import com.sebin.secondhand_market.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * STOMP 인증. CONNECT 프레임의 {@code Authorization} 네이티브 헤더에서 JWT를 읽는다.
 *
 * <p>핸드셰이크가 아니라 CONNECT에서 인증하는 이유: 브라우저의 WebSocket API는 핸드셰이크 요청에
 * 임의 헤더를 넣을 수 없다. 반면 CONNECT 프레임의 헤더는 stompjs {@code connectHeaders}로 지정할 수 있다.
 */
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

  private static final Logger log = LogManager.getLogger(JwtChannelInterceptor.class);
  private final JwtProvider jwtProvider;

  private static final String BEARER_PREFIX = "Bearer ";
  private static final int BEARER_PREFIX_LENGTH = 7;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {

    // wrap()이 아니라 getAccessor()를 쓴다. wrap()은 복사본을 만들기 때문에 여기서 setUser를 해도
    // StompSubProtocolHandler가 보지 못하고, 인증 결과가 세션에 남지 않는다.
    // 그러면 SUBSCRIBE·SEND마다 다시 토큰을 받아야 하는데 브라우저 클라이언트는 그렇게 동작하지 않는다.
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
      return message;
    }

    String token = resolveToken(accessor);

    // 인증 실패한 CONNECT는 여기서 끊는다. 핸드셰이크에서 토큰을 보지 않게 된 이후로
    // 이 지점이 유일한 인증 관문이다 — 통과시키면 principal 없는 세션이 그대로 살아남는다.
    if (token == null || !jwtProvider.validate(token)) {
      throw new MessageDeliveryException(message, "STOMP CONNECT에 유효한 인증 토큰이 필요합니다.");
    }

    Authentication authentication = jwtProvider.getAuthentication(token);
    accessor.setUser(authentication);

    log.info("STOMP CONNECT user={}", authentication.getName());
    return message;
  }

  private String resolveToken(StompHeaderAccessor accessor) {
    String authHeader = accessor.getFirstNativeHeader("Authorization");
    if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
      return authHeader.substring(BEARER_PREFIX_LENGTH);
    }
    return null;
  }
}
