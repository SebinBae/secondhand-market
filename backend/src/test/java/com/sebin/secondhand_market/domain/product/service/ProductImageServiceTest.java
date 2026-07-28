package com.sebin.secondhand_market.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sebin.secondhand_market.domain.product.entity.ProductEntity;
import com.sebin.secondhand_market.domain.product.entity.ProductImageEntity;
import com.sebin.secondhand_market.domain.product.exception.ImageCountExceededException;
import com.sebin.secondhand_market.domain.product.exception.ImageTooLargeException;
import com.sebin.secondhand_market.domain.product.exception.InvalidImageFormatException;
import com.sebin.secondhand_market.domain.product.exception.ProductAccessDeniedException;
import com.sebin.secondhand_market.domain.product.repository.ProductImageRepository;
import com.sebin.secondhand_market.domain.product.repository.ProductRepository;
import com.sebin.secondhand_market.domain.user.entity.UserEntity;
import com.sebin.secondhand_market.global.storage.ImageStorage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 업로드 검증 규칙 단위 테스트. Supabase 호출은 {@link ImageStorage} 목으로 대체한다.
 */
@ExtendWith(MockitoExtension.class)
class ProductImageServiceTest {

  @Mock
  private ProductRepository productRepository;
  @Mock
  private ProductImageRepository productImageRepository;
  @Mock
  private ImageStorage imageStorage;
  @Mock
  private ProductEntity product;
  @Mock
  private UserEntity seller;

  @InjectMocks
  private ProductImageService productImageService;

  private final UUID productId = UUID.randomUUID();
  private final UUID ownerId = UUID.randomUUID();

  private MockMultipartFile jpeg(String name) {
    return new MockMultipartFile("files", name, "image/jpeg", new byte[]{1, 2, 3});
  }

  private void givenOwnedProduct(long existingImageCount) {
    given(productRepository.findById(productId)).willReturn(Optional.of(product));
    given(product.getSeller()).willReturn(seller);
    given(seller.getId()).willReturn(ownerId);
    given(productImageRepository.countByProductId(productId)).willReturn(existingImageCount);
  }

  @Test
  @DisplayName("지원하지 않는 형식이면 InvalidImageFormatException, 저장소는 호출되지 않는다")
  void rejectsInvalidFormat() {
    givenOwnedProduct(0);
    MockMultipartFile txt = new MockMultipartFile("files", "a.txt", "text/plain", new byte[]{1});

    assertThatThrownBy(() -> productImageService.upload(productId, List.of(txt), ownerId))
        .isInstanceOf(InvalidImageFormatException.class);

    verifyNoInteractions(imageStorage);
  }

  @Test
  @DisplayName("파일이 5MB를 초과하면 ImageTooLargeException")
  void rejectsTooLargeFile() {
    givenOwnedProduct(0);
    MultipartFile big = org.mockito.Mockito.mock(MultipartFile.class);
    given(big.getContentType()).willReturn("image/jpeg");
    given(big.getSize()).willReturn(5L * 1024 * 1024 + 1);

    assertThatThrownBy(() -> productImageService.upload(productId, List.of(big), ownerId))
        .isInstanceOf(ImageTooLargeException.class);

    verifyNoInteractions(imageStorage);
  }

  @Test
  @DisplayName("기존 + 신규 이미지 합이 10장을 초과하면 ImageCountExceededException")
  void rejectsWhenCountExceeded() {
    givenOwnedProduct(10); // 이미 10장 → 1장만 더 올려도 초과

    assertThatThrownBy(() -> productImageService.upload(productId, List.of(jpeg("a.jpg")), ownerId))
        .isInstanceOf(ImageCountExceededException.class);

    verifyNoInteractions(imageStorage);
  }

  @Test
  @DisplayName("본인 상품이 아니면 ProductAccessDeniedException")
  void rejectsNonOwner() {
    given(productRepository.findById(productId)).willReturn(Optional.of(product));
    given(product.getSeller()).willReturn(seller);
    given(seller.getId()).willReturn(UUID.randomUUID()); // 다른 사용자 소유

    assertThatThrownBy(() -> productImageService.upload(productId, List.of(jpeg("a.jpg")), ownerId))
        .isInstanceOf(ProductAccessDeniedException.class);

    verifyNoInteractions(imageStorage);
  }

  @Test
  @DisplayName("정상 요청이면 각 파일을 저장소에 업로드하고 URL 목록을 반환한다")
  void uploadsValidImages() {
    givenOwnedProduct(0);
    given(imageStorage.upload(any(), any(), any())).willReturn("https://cdn/img.jpg");

    List<String> urls = productImageService.upload(
        productId, List.of(jpeg("a.jpg"), jpeg("b.jpg")), ownerId);

    assertThat(urls).hasSize(2);
    verify(imageStorage, times(2)).upload(any(), any(), any());
    verify(productImageRepository, times(2)).save(any(ProductImageEntity.class));
  }

  // ── 상품 없는 업로드(AI 등록 초안용) ──────────────────────────

  @Test
  @DisplayName("상품 없이 업로드하면 저장소에만 올리고 DB 행은 만들지 않는다")
  void uploadDetachedDoesNotPersistRows() {
    given(imageStorage.upload(any(), any(), any())).willReturn("https://cdn/img.jpg");

    List<String> urls = productImageService.uploadDetached(List.of(jpeg("a.jpg"), jpeg("b.jpg")));

    assertThat(urls).hasSize(2);
    verify(imageStorage, times(2)).upload(any(), any(), any());
    // 아직 붙일 상품이 없으므로 product_images 행이 생기면 안 된다
    verifyNoInteractions(productImageRepository, productRepository);
  }

  @Test
  @DisplayName("상품 없는 업로드에도 형식 검증이 적용된다")
  void uploadDetachedRejectsInvalidFormat() {
    MockMultipartFile txt = new MockMultipartFile("files", "a.txt", "text/plain", new byte[]{1});

    assertThatThrownBy(() -> productImageService.uploadDetached(List.of(txt)))
        .isInstanceOf(InvalidImageFormatException.class);

    verifyNoInteractions(imageStorage);
  }

  @Test
  @DisplayName("상품 없는 업로드에도 크기 상한이 적용된다")
  void uploadDetachedRejectsTooLargeFile() {
    MultipartFile big = org.mockito.Mockito.mock(MultipartFile.class);
    given(big.getContentType()).willReturn("image/jpeg");
    given(big.getSize()).willReturn(5L * 1024 * 1024 + 1);

    assertThatThrownBy(() -> productImageService.uploadDetached(List.of(big)))
        .isInstanceOf(ImageTooLargeException.class);

    verifyNoInteractions(imageStorage);
  }

  @Test
  @DisplayName("상품 없는 업로드도 한 번에 10장을 넘길 수 없다")
  void uploadDetachedRejectsWhenCountExceeded() {
    List<MultipartFile> eleven = java.util.stream.IntStream.range(0, 11)
        .<MultipartFile>mapToObj(i -> jpeg(i + ".jpg"))
        .toList();

    assertThatThrownBy(() -> productImageService.uploadDetached(eleven))
        .isInstanceOf(ImageCountExceededException.class);

    verifyNoInteractions(imageStorage);
  }
}
