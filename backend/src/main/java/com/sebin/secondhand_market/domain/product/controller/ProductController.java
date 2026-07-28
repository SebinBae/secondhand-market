package com.sebin.secondhand_market.domain.product.controller;

import com.sebin.secondhand_market.domain.product.dto.request.ListingDraftRequest;
import com.sebin.secondhand_market.domain.product.dto.request.ProductCreateRequest;
import com.sebin.secondhand_market.domain.product.dto.request.ProductSearchRequest;
import com.sebin.secondhand_market.domain.product.dto.request.ProductStatusChangeRequest;
import com.sebin.secondhand_market.domain.product.dto.response.ImageUploadResponse;
import com.sebin.secondhand_market.domain.product.dto.response.ListingDraftResponse;
import com.sebin.secondhand_market.domain.product.dto.response.ProductCreatedResponse;
import com.sebin.secondhand_market.domain.product.dto.response.ProductDetailResponse;
import com.sebin.secondhand_market.domain.product.dto.response.ProductImageUploadResponse;
import com.sebin.secondhand_market.domain.product.dto.response.ProductResponse;
import com.sebin.secondhand_market.domain.product.service.ProductAiAssistService;
import com.sebin.secondhand_market.domain.product.service.ProductImageService;
import com.sebin.secondhand_market.domain.product.service.ProductReadService;
import com.sebin.secondhand_market.domain.product.service.ProductService;
import com.sebin.secondhand_market.domain.product.type.ProductSortType;
import com.sebin.secondhand_market.domain.product.type.ProductStatus;
import com.sebin.secondhand_market.global.common.PageResponse;
import com.sebin.secondhand_market.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

  private final ProductService productService;
  private final ProductReadService productReadService;
  private final ProductImageService productImageService;
  private final ProductAiAssistService productAiAssistService;

  // 상품 등록
  @Operation(summary = "상품 등록")
  @PostMapping
  public ResponseEntity<ProductCreatedResponse> create(
      @RequestBody @Valid ProductCreateRequest request,
      @AuthenticationPrincipal UserPrincipal userPrincipal
  ) {
    UUID productId = productService.create(request, userPrincipal.getUserId());

    ProductCreatedResponse response = new ProductCreatedResponse(
        HttpStatus.CREATED,
        "상품이 성공적으로 등록완료 되었습니다!",
        productId
    );

    return ResponseEntity.ok(response);
  }

  // 상품 수정
  @Operation(summary = "상품 수정")
  @PutMapping("/{productId}")
  public ResponseEntity<ProductCreatedResponse> update(
      @PathVariable UUID productId,
      @RequestBody @Valid ProductCreateRequest request,
      @AuthenticationPrincipal UserPrincipal userPrincipal) {
    productService.update(productId, request, userPrincipal.getUserId());

    ProductCreatedResponse response = new ProductCreatedResponse(
        HttpStatus.OK,
        "상품이 성공적으로 수정되었습니다!",
        productId
    );

    return ResponseEntity.ok(response);
  }

  // 상품 삭제
  @Operation(summary = "상품 삭제")
  @DeleteMapping("/{productId}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID productId,
      @AuthenticationPrincipal UserPrincipal userPrincipal
  ) {
    productService.delete(productId, userPrincipal.getUserId());

    return ResponseEntity.noContent().build();
  }

  // 상품 상태 변경
  @Operation(summary = "상품 상태 변경")
  @PatchMapping("/{productId}/status")
  public ResponseEntity<ProductCreatedResponse> changeStatus(
      @PathVariable UUID productId,
      @RequestBody @Valid ProductStatusChangeRequest request,
      @AuthenticationPrincipal UserPrincipal userPrincipal
  ) {
    productService.changeStatus(productId, request.getProductStatus(), userPrincipal.getUserId());

    ProductCreatedResponse response = new ProductCreatedResponse(
        HttpStatus.OK,
        "상품 상태가 성공적으로 수정되었습니다!",
        productId
    );

    return ResponseEntity.ok(response);
  }

  // 상품 검색
  @Operation(summary = "상품 검색")
  @GetMapping
  public ResponseEntity<PageResponse<ProductResponse>> search(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) ProductStatus status,
      @RequestParam(required = false) ProductSortType sortType,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size
  ) {
    ProductSearchRequest request = new ProductSearchRequest(keyword, status, sortType);

    // 변환은 서비스(트랜잭션 안)에서 끝난다 — 여기서 매핑하면 지연 로딩이 세션 밖에서 터진다.
    Page<ProductResponse> responses = productReadService.search(request, page, size);

    return ResponseEntity.ok(PageResponse.from(responses));
  }

  // 상품 상세 조회 (이미지 URL 포함)
  @Operation(summary = "상품 상세 조회")
  @GetMapping("/{productId}")
  public ResponseEntity<ProductDetailResponse> detail(@PathVariable UUID productId) {
    return ResponseEntity.ok(productReadService.getProductDetail(productId));
  }

  // AI 등록 초안 (제안일 뿐 저장하지 않는다. ai-service 장애 시 aiAvailable=false)
  @Operation(summary = "AI 등록 초안 생성")
  @PostMapping("/listing-drafts")
  public ResponseEntity<ListingDraftResponse> createListingDraft(
      @RequestBody @Valid ListingDraftRequest request
  ) {
    return ResponseEntity.ok(productAiAssistService.createDraft(request));
  }

  // 상품 등록 전 이미지 업로드 (AI 초안 입력용). 저장소에만 올리고 상품에는 붙이지 않는다.
  @Operation(summary = "상품 등록 전 이미지 업로드")
  @PostMapping("/images")
  public ResponseEntity<ImageUploadResponse> uploadImagesBeforeCreate(
      @RequestPart("files") List<MultipartFile> files
  ) {
    return ResponseEntity.ok(new ImageUploadResponse(productImageService.uploadDetached(files)));
  }

  // 상품 이미지 업로드 (본인 상품, 여러 장, 상품당 최대 10장)
  @Operation(summary = "상품 이미지 업로드")
  @PostMapping("/{productId}/images")
  public ResponseEntity<ProductImageUploadResponse> uploadImages(
      @PathVariable UUID productId,
      @RequestPart("files") List<MultipartFile> files,
      @AuthenticationPrincipal UserPrincipal userPrincipal
  ) {
    List<String> imageUrls =
        productImageService.upload(productId, files, userPrincipal.getUserId());

    return ResponseEntity.ok(new ProductImageUploadResponse(productId, imageUrls));
  }

}
