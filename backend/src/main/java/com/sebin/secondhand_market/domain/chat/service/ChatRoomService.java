package com.sebin.secondhand_market.domain.chat.service;

import com.sebin.secondhand_market.domain.chat.dto.response.ChatRoomResponse;
import com.sebin.secondhand_market.domain.chat.entity.ChatRoomEntity;
import com.sebin.secondhand_market.domain.chat.repository.ChatRoomRepository;
import com.sebin.secondhand_market.domain.product.entity.ProductEntity;
import com.sebin.secondhand_market.domain.product.service.ProductReadService;
import com.sebin.secondhand_market.domain.user.entity.UserEntity;
import com.sebin.secondhand_market.domain.user.service.UserService;
import com.sebin.secondhand_market.domain.chat.exception.BuyerNotFoundException;
import com.sebin.secondhand_market.domain.chat.exception.InvalidChatTargetException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ChatRoomService {

  private final ChatRoomRepository chatRoomRepository;
  private final ProductReadService productReadService;
  private final UserService userService;

  /**
   * 상품과 구매자 기준 채팅방 생성(이미 존재하면 조회함)
   */
  public ChatRoomResponse createOrGetRoom(UUID productId, UUID buyerId) {
    // 상품 조회
    ProductEntity product = productReadService.getProductById(productId);

    // 구매자 조회
    UserEntity buyer = userService.findById(buyerId)
        .orElseThrow(BuyerNotFoundException::new);

    // 판매자
    UserEntity seller = product.getSeller();

    // 판매자가 자기 자신의 상품에 채팅 거는 것을 방지하기 위한 코드
    if (seller.getId().equals(buyerId)) {
      throw new InvalidChatTargetException();
    }

    // 이미 채팅방이 존재하는 경우 반환하고 없는 경우(null) 새로 생성함.
    return chatRoomRepository.findByProductIdAndBuyerId(productId, buyerId)
        .map(ChatRoomResponse::from)
        .orElseGet(() -> {
          ChatRoomEntity room = ChatRoomEntity.create(product, seller, buyer);
          ChatRoomEntity saved = chatRoomRepository.save(room);
          return ChatRoomResponse.from(saved);
        });
  }

  /**
   * 해당 사용자가 채팅방 참여자(판매자 또는 구매자)인지 확인한다.
   *
   * <p>구독 인가에서 쓴다. 없는 방이면 참여자도 아니므로 예외 대신 false를 돌려준다 —
   * 존재하지 않는 방과 남의 방을 구분해 알려줄 이유가 없다.
   */
  @Transactional(readOnly = true)
  public boolean isParticipant(UUID roomId, UUID userId) {
    return chatRoomRepository.findById(roomId)
        .map(room -> room.getSeller().getId().equals(userId)
            || room.getBuyer().getId().equals(userId))
        .orElse(false);
  }

  // 채팅방 조회
  public Page<ChatRoomResponse> getMyChatRooms(UUID userId, int page, int size) {

    Pageable pageable = PageRequest.of(
        page, size, Sort.by(Direction.DESC, "LastMessageAt")
    );

    return chatRoomRepository.findMyChatRooms(userId, pageable)
        .map(ChatRoomResponse::from);
  }

}
