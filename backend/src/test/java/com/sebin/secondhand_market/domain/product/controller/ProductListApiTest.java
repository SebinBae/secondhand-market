package com.sebin.secondhand_market.domain.product.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sebin.secondhand_market.domain.product.entity.ProductEntity;
import com.sebin.secondhand_market.domain.product.repository.ProductImageRepository;
import com.sebin.secondhand_market.domain.product.repository.ProductRepository;
import com.sebin.secondhand_market.domain.product.type.ProductCategory;
import com.sebin.secondhand_market.domain.product.type.ProductStatus;
import com.sebin.secondhand_market.domain.user.entity.UserEntity;
import com.sebin.secondhand_market.domain.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 상품 목록 API를 실제 HTTP 경로로 검증한다.
 *
 * <p>이 테스트가 따로 필요한 이유: {@code ProductListResponseTest}는 {@code @DataJpaTest}라
 * 테스트 메서드 전체가 하나의 트랜잭션 안에서 돌고 세션이 열린 채로 유지된다. 그래서 DTO 변환이
 * 트랜잭션 밖에서 일어나는지를 판별할 수 없다.
 *
 * <p>실제 애플리케이션은 {@code open-in-view=false}라 컨트롤러에 세션이 없다. 목록 응답이 대표
 * 이미지를 읽으면서 지연 로딩 컬렉션을 건드리는데, 변환이 서비스 밖으로 나가면
 * LazyInitializationException이 난다 — 슬라이스 테스트로는 잡히지 않고 실행해야만 드러난다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductListApiTest {

  @Autowired
  MockMvc mockMvc;
  @Autowired
  UserRepository userRepository;
  @Autowired
  ProductRepository productRepository;
  @Autowired
  ProductImageRepository productImageRepository;

  @BeforeEach
  void setUp() {
    UserEntity seller = userRepository.save(
        new UserEntity(UUID.randomUUID() + "@example.com", "encoded", "민트초코러버"));

    ProductEntity withImages = new ProductEntity(
        "허먼밀러 세이체어 " + UUID.randomUUID(), 185000, "설명",
        ProductCategory.FURNITURE, ProductStatus.SELLING, seller, "성수동");
    withImages.addImages(List.of("https://cdn/a.jpg", "https://cdn/b.jpg"));
    productRepository.save(withImages);

    productRepository.save(new ProductEntity(
        "이미지없는상품 " + UUID.randomUUID(), 10000, "설명",
        ProductCategory.BOOK, ProductStatus.SELLING, seller, null));
  }

  @Test
  @WithMockUser
  @DisplayName("목록 조회가 세션 밖 지연 로딩 없이 성공한다")
  void listDoesNotBlowUpOutsideTransaction() throws Exception {
    mockMvc.perform(get("/api/products").param("page", "0").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].id").exists());
  }

  @Test
  @WithMockUser
  @DisplayName("목록 응답이 썸네일과 거래 장소를 담는다")
  void listCarriesThumbnailAndLocation() throws Exception {
    mockMvc.perform(get("/api/products")
            .param("page", "0").param("size", "50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.thumbnailUrl == 'https://cdn/a.jpg')]").exists())
        .andExpect(jsonPath("$.content[?(@.location == '성수동')]").exists());
  }
}
