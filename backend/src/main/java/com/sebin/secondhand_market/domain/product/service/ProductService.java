package com.sebin.secondhand_market.domain.product.service;

import com.sebin.secondhand_market.domain.product.dto.request.ProductCreateRequest;
import com.sebin.secondhand_market.domain.product.entity.ProductEntity;
import com.sebin.secondhand_market.domain.product.event.ProductCreatedEvent;
import com.sebin.secondhand_market.domain.product.repository.ProductRepository;
import com.sebin.secondhand_market.domain.product.type.ProductStatus;
import com.sebin.secondhand_market.domain.user.entity.UserEntity;
import com.sebin.secondhand_market.domain.user.service.UserService;
import com.sebin.secondhand_market.domain.product.exception.ProductAccessDeniedException;
import com.sebin.secondhand_market.domain.product.exception.ProductNotFoundException;
import com.sebin.secondhand_market.domain.user.exception.UserNotFoundException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
@RequiredArgsConstructor
public class ProductService {

  private final ProductRepository productRepository;
  private final UserService userService;
  private final ApplicationEventPublisher eventPublisher;

  // 상품 등록 처리
  public UUID create(ProductCreateRequest request, UUID userId) {
    UserEntity seller = userService.findById(userId).orElseThrow(
        UserNotFoundException::new
    );

    ProductEntity product = new ProductEntity(
        request.getTitle(),
        request.getPrice(),
        request.getDescription(),
        request.getProductCategory(),
        ProductStatus.SELLING,
        seller,
        request.getLocation()
    );

    // 등록 전 업로드한 이미지를 연결한다 (AI 초안 흐름). 없으면 아무것도 하지 않는다.
    product.addImages(request.getImageUrls());

    ProductEntity saved = productRepository.save(product);

    // 저장 성공 후 이벤트 발행 — 실제 수신은 커밋 이후(AFTER_COMMIT)
    eventPublisher.publishEvent(new ProductCreatedEvent(
        saved.getId(),
        seller.getId(),
        saved.getTitle(),
        Instant.now()
    ));

    return saved.getId();

  }

  // 상품 정보 수정
  public void update(UUID productId, ProductCreateRequest request, UUID userId) {

    ProductEntity product = productRepository.findById(productId)
        .orElseThrow(ProductNotFoundException::new);

    // 판매자 본인 상품이 아닌 경우 로직 처리
    if (!product.getSeller().getId().equals(userId)) {
      throw new ProductAccessDeniedException();
    }

    product.update(
        request.getTitle(),
        request.getPrice(),
        request.getDescription(),
        request.getProductCategory(),
        request.getLocation()
    );

  }

  // 상품 정보 삭제
  public void delete(UUID productId, UUID userId) {

    ProductEntity product = productRepository.findById(productId)
        .orElseThrow(ProductNotFoundException::new);

    // 판매자 본인 상품이 아닌 경우 로직 처리
    if (!product.getSeller().getId().equals(userId)) {
      throw new ProductAccessDeniedException();
    }

    productRepository.delete(product);

  }

  // 상품 상태 변경(판매중 -> 판매완료 / 판매완료 -> 판매중)
  public void changeStatus(
      UUID productId, ProductStatus productStatus, UUID userId
  ) {
    ProductEntity product = productRepository.findById(productId)
        .orElseThrow(ProductNotFoundException::new);

    // 판매자 본인 상품이 아닌 경우 로직 처리
    if (!product.getSeller().getId().equals(userId)) {
      throw new ProductAccessDeniedException();
    }

    product.changeStatus(productStatus);
  }


}
