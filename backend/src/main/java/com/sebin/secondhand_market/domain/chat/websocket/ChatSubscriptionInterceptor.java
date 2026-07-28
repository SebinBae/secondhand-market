package com.sebin.secondhand_market.domain.chat.websocket;

import com.sebin.secondhand_market.domain.chat.service.ChatRoomService;
import com.sebin.secondhand_market.global.websocket.StompDestination;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * 채팅방 구독 인가.
 *
 * <p>지금까지 SUBSCRIBE에는 아무 검사도 없었다. 핸드셰이크의 JWT 검사가 유일한 방어선이었는데,
 * 브라우저는 핸드셰이크에 Authorization 헤더를 붙일 수 없어 그 검사를 걷어내야 했다. 그대로 두면
 * 아무나 {@code /topic/chat.{roomId}}를 구독해 남의 대화를 읽을 수 있으므로 여기서 막는다.
 *
 * <p>참여자 판정은 chat 도메인 지식이라 global이 아니라 이 패키지에 둔다.
 */
@Component
@RequiredArgsConstructor
public class ChatSubscriptionInterceptor implements ChannelInterceptor {

  private final ChatRoomService chatRoomService;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

    if (!StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
      return message;
    }

    String destination = accessor.getDestination();
    if (destination == null || !destination.startsWith(StompDestination.CHAT_TOPIC_PREFIX)) {
      return message;
    }

    Principal principal = accessor.getUser();
    if (principal == null) {
      throw new MessageDeliveryException(message, "인증되지 않은 사용자는 채팅방을 구독할 수 없습니다.");
    }

    UUID roomId = parseRoomId(destination);
    if (roomId == null) {
      throw new MessageDeliveryException(message, "채팅방 구독 경로가 올바르지 않습니다.");
    }

    if (!chatRoomService.isParticipant(roomId, UUID.fromString(principal.getName()))) {
      throw new MessageDeliveryException(message, "참여 중인 채팅방만 구독할 수 있습니다.");
    }

    return message;
  }

  /** {@code /topic/chat.{roomId}}에서 roomId를 꺼낸다. UUID가 아니면 null. */
  private UUID parseRoomId(String destination) {
    String raw = destination.substring(StompDestination.CHAT_TOPIC_PREFIX.length());
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
