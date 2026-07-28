package com.sebin.secondhand_market.domain.product.service;

import com.sebin.secondhand_market.domain.product.entity.ProductEntity;
import com.sebin.secondhand_market.domain.product.entity.ProductImageEntity;
import com.sebin.secondhand_market.domain.product.exception.ImageCountExceededException;
import com.sebin.secondhand_market.domain.product.exception.ImageTooLargeException;
import com.sebin.secondhand_market.domain.product.exception.InvalidImageFormatException;
import com.sebin.secondhand_market.domain.product.exception.ProductAccessDeniedException;
import com.sebin.secondhand_market.domain.product.exception.ProductNotFoundException;
import com.sebin.secondhand_market.domain.product.repository.ProductImageRepository;
import com.sebin.secondhand_market.domain.product.repository.ProductRepository;
import com.sebin.secondhand_market.global.storage.ImageStorage;
import com.sebin.secondhand_market.global.storage.ImageUploadFailedException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductImageService {

  private static final int MAX_IMAGES_PER_PRODUCT = 10;
  private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5MB
  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp");

  private final ProductRepository productRepository;
  private final ProductImageRepository productImageRepository;
  private final ImageStorage imageStorage;

  /**
   * 상품에 이미지들을 업로드한다. 본인 상품만 가능하며, 형식/크기/개수 검증을 저장 이전에 모두 수행한다.
   *
   * @return 저장된 이미지 URL 목록(표시 순서대로)
   */
  public List<String> upload(UUID productId, List<MultipartFile> files, UUID userId) {
    ProductEntity product = productRepository.findById(productId)
        .orElseThrow(ProductNotFoundException::new);

    // 본인 상품 확인
    if (!product.getSeller().getId().equals(userId)) {
      throw new ProductAccessDeniedException();
    }

    // 개수: 기존 + 이번 요청 합이 상한 초과면 거부
    long existingCount = productImageRepository.countByProductId(productId);
    if (existingCount + files.size() > MAX_IMAGES_PER_PRODUCT) {
      throw new ImageCountExceededException();
    }

    // 형식/크기: 부분 업로드 방지를 위해 저장 전에 전부 검증
    for (MultipartFile file : files) {
      validate(file);
    }

    List<String> urls = new ArrayList<>();
    int order = (int) existingCount;
    for (MultipartFile file : files) {
      String url = imageStorage.upload(
          readBytes(file), file.getOriginalFilename(), file.getContentType());
      productImageRepository.save(new ProductImageEntity(product, url, order++));
      urls.add(url);
    }
    return urls;
  }

  /**
   * 상품에 붙이지 않고 이미지만 저장소에 올린다.
   *
   * <p>AI 등록은 사진이 먼저 있어야 초안을 만들 수 있는데, 초안이 나와야 상품을 만들 수 있다.
   * {@link #upload}는 productId를 요구하므로 이 순환을 풀 수 없어 별도 경로를 둔다.
   * 여기서 받은 URL은 상품 등록 요청의 {@code imageUrls}로 넘겨 상품에 연결한다.
   *
   * <p>연결되지 않은 파일은 저장소에 남는다. 정리 배치는 두지 않는다 — 현 단계에서 과하다.
   *
   * @return 저장된 이미지 URL 목록(요청 순서대로)
   */
  public List<String> uploadDetached(List<MultipartFile> files) {
    if (files.size() > MAX_IMAGES_PER_PRODUCT) {
      throw new ImageCountExceededException();
    }

    // 부분 업로드 방지를 위해 저장 전에 전부 검증
    for (MultipartFile file : files) {
      validate(file);
    }

    List<String> urls = new ArrayList<>();
    for (MultipartFile file : files) {
      urls.add(imageStorage.upload(
          readBytes(file), file.getOriginalFilename(), file.getContentType()));
    }
    return urls;
  }

  private void validate(MultipartFile file) {
    if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
      throw new InvalidImageFormatException();
    }
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new ImageTooLargeException();
    }
  }

  private byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new ImageUploadFailedException();
    }
  }
}
