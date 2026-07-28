package com.sebin.secondhand_market.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sebin.secondhand_market.domain.product.dto.request.ProductSearchRequest;
import com.sebin.secondhand_market.domain.product.dto.response.ProductDetailResponse;
import com.sebin.secondhand_market.domain.product.dto.response.ProductResponse;
import com.sebin.secondhand_market.domain.product.entity.ProductEntity;
import com.sebin.secondhand_market.domain.product.entity.ProductImageEntity;
import com.sebin.secondhand_market.domain.product.repository.search.ProductQueryRepositoryImpl;
import com.sebin.secondhand_market.domain.product.type.ProductCategory;
import com.sebin.secondhand_market.domain.product.type.ProductStatus;
import com.sebin.secondhand_market.domain.user.entity.UserEntity;
import com.sebin.secondhand_market.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * 목록/상세 응답 매핑과 이미지 조회 쿼리 수 검증.
 *
 * <p>목록 응답에 썸네일이 들어가면서 상품마다 images 컬렉션을 읽게 됐다. {@code @BatchSize}가 빠지면
 * 페이지 크기만큼 N+1이 나므로, 쿼리 수를 세는 테스트로 회귀를 막는다.
 */
@DataJpaTest
@Import({QuerydslConfig.class, ProductQueryRepositoryImpl.class})
class ProductListResponseTest {

  @Autowired
  EntityManager em;
  @Autowired
  ProductQueryRepositoryImpl productQueryRepository;

  private UserEntity persistSeller() {
    UserEntity seller = new UserEntity("seller@example.com", "encoded", "민트초코러버");
    em.persist(seller);
    return seller;
  }

  private ProductEntity persistProduct(UserEntity seller, String title, int imageCount) {
    ProductEntity product = new ProductEntity(
        title, 10000, "설명", ProductCategory.FURNITURE, ProductStatus.SELLING, seller);
    em.persist(product);

    for (int i = 0; i < imageCount; i++) {
      em.persist(new ProductImageEntity(product, "https://cdn/" + title + "-" + i + ".jpg", i));
    }
    return product;
  }

  private Statistics statistics() {
    return em.unwrap(Session.class).getSessionFactory().getStatistics();
  }

  @Test
  @DisplayName("목록 응답에 상품 id와 대표 이미지가 실린다")
  void listResponseCarriesIdAndThumbnail() {
    UserEntity seller = persistSeller();
    ProductEntity product = persistProduct(seller, "책상", 3);
    em.flush();
    em.clear();

    Page<ProductEntity> page =
        productQueryRepository.search(new ProductSearchRequest(null, null, null), PageRequest.of(0, 10));
    ProductResponse response = ProductResponse.from(page.getContent().get(0));

    assertThat(response.getId()).isEqualTo(product.getId());
    // displayOrder 0번이 대표 이미지
    assertThat(response.getThumbnailUrl()).isEqualTo("https://cdn/책상-0.jpg");
    assertThat(response.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("이미지가 없는 상품의 thumbnailUrl은 null이다")
  void thumbnailIsNullWithoutImages() {
    UserEntity seller = persistSeller();
    persistProduct(seller, "이미지없는상품", 0);
    em.flush();
    em.clear();

    Page<ProductEntity> page =
        productQueryRepository.search(new ProductSearchRequest(null, null, null), PageRequest.of(0, 10));

    assertThat(ProductResponse.from(page.getContent().get(0)).getThumbnailUrl()).isNull();
  }

  @Test
  @DisplayName("상세 응답에 판매자 닉네임이 실린다")
  void detailResponseCarriesSellerNickname() {
    UserEntity seller = persistSeller();
    ProductEntity product = persistProduct(seller, "의자", 2);
    em.flush();
    em.clear();

    ProductDetailResponse response =
        ProductDetailResponse.from(em.find(ProductEntity.class, product.getId()));

    assertThat(response.getSellerNickname()).isEqualTo("민트초코러버");
    assertThat(response.getImageUrls()).containsExactly("https://cdn/의자-0.jpg", "https://cdn/의자-1.jpg");
  }

  @Test
  @DisplayName("목록 10건의 썸네일을 읽어도 이미지 조회는 배치로 묶여 상품 수에 비례하지 않는다")
  void thumbnailDoesNotCauseNPlusOne() {
    UserEntity seller = persistSeller();
    for (int i = 0; i < 10; i++) {
      persistProduct(seller, "상품" + i, 2);
    }
    em.flush();
    em.clear();

    Statistics statistics = statistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();

    Page<ProductEntity> page =
        productQueryRepository.search(new ProductSearchRequest(null, null, null), PageRequest.of(0, 10));
    List<ProductResponse> responses = page.getContent().stream().map(ProductResponse::from).toList();

    assertThat(responses).hasSize(10);
    assertThat(responses).allSatisfy(r -> assertThat(r.getThumbnailUrl()).isNotNull());

    // content 조회 1 + count 1 + 이미지 배치 1 = 3. 상품별로 이미지를 읽으면 12가 된다.
    assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
  }
}
