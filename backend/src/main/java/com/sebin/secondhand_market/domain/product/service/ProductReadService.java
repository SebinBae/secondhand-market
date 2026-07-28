package com.sebin.secondhand_market.domain.product.service;

import com.sebin.secondhand_market.domain.product.dto.request.ProductSearchRequest;
import com.sebin.secondhand_market.domain.product.dto.response.ProductDetailResponse;
import com.sebin.secondhand_market.domain.product.dto.response.ProductResponse;
import com.sebin.secondhand_market.domain.product.entity.ProductEntity;
import com.sebin.secondhand_market.domain.product.exception.ProductNotFoundException;
import com.sebin.secondhand_market.domain.product.repository.ProductRepository;
import com.sebin.secondhand_market.domain.product.repository.search.ProductQueryRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductReadService {

  private final ProductQueryRepository productQueryRepository;
  private final ProductRepository productRepository;

  /**
   * 상품 검색. <b>DTO 변환까지 이 안에서 끝낸다.</b>
   *
   * <p>엔티티를 그대로 돌려주면 안 된다. {@code ProductResponse.from}이 대표 이미지를 읽으려고
   * 지연 로딩 컬렉션을 건드리는데, {@code open-in-view=false}라 컨트롤러에는 세션이 없어
   * LazyInitializationException이 난다.
   */
  @Transactional(readOnly = true)
  public Page<ProductResponse> search(ProductSearchRequest request, int page, int size){
    Pageable pageable = PageRequest.of(page, size);

    return productQueryRepository.search(request, pageable)
        .map(ProductResponse::from);
  }

  // 타 도메인 공개 조회 창구 — 상품 단건 조회(없으면 예외)
  @Transactional(readOnly = true)
  public ProductEntity getProductById(UUID productId) {
    return productRepository.findById(productId)
        .orElseThrow(ProductNotFoundException::new);
  }

  // 상품 상세 조회 — 이미지 URL 포함. 단건이라 images 지연 로딩은 N+1 아님.
  @Transactional(readOnly = true)
  public ProductDetailResponse getProductDetail(UUID productId) {
    ProductEntity product = productRepository.findById(productId)
        .orElseThrow(ProductNotFoundException::new);
    return ProductDetailResponse.from(product);
  }
}
