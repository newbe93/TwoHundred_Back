package org.duckdns.bidbuy.app.article.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.duckdns.bidbuy.app.article.domain.Article;
import org.duckdns.bidbuy.app.article.domain.LikeArticle;
import org.duckdns.bidbuy.app.article.domain.ProductImage;
import org.duckdns.bidbuy.app.article.dto.ArticleDetailResponse;
import org.duckdns.bidbuy.app.article.dto.ArticleRequest;
import org.duckdns.bidbuy.app.article.dto.ArticleResponse;
import org.duckdns.bidbuy.app.article.dto.ArticleSummaryResponse;
import org.duckdns.bidbuy.app.article.exception.ArticleNoPermitException;
import org.duckdns.bidbuy.app.article.exception.ArticleNotExistException;
import org.duckdns.bidbuy.app.article.exception.WriterNotFoundException;
import org.duckdns.bidbuy.app.article.repository.ArticleRepository;
import org.duckdns.bidbuy.app.article.repository.LikeArticleRepository;
import org.duckdns.bidbuy.app.article.repository.ProductImageRepository;
import org.duckdns.bidbuy.app.chat.repository.ChatRoomRepository;
import org.duckdns.bidbuy.app.offer.dto.OfferResponse;
import org.duckdns.bidbuy.app.offer.repository.OfferRepository;
import org.duckdns.bidbuy.app.offer.service.OfferService;
import org.duckdns.bidbuy.app.user.domain.User;
import org.duckdns.bidbuy.app.user.exception.ForbiddenException;
import org.duckdns.bidbuy.app.user.exception.NotLoggedInException;
import org.duckdns.bidbuy.app.user.repository.UserRepository;
import org.duckdns.bidbuy.global.auth.domain.CustomUserDetails;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final ProductImageRepository productImageRepository;
    private final ImageUploadService imageUploadService;
    private final OfferService offerService;
    private final LikeArticleRepository likeArticleRepository;
    private final OfferRepository offerRepository;
    private final ChatRoomRepository chatRoomRepository;

    @Transactional
    public ArticleResponse createArticle(ArticleRequest requestDTO, MultipartFile[] images) throws IOException {
        CustomUserDetails principal = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = principal.getUser().getId();

        User writer = userRepository.findById(userId).orElseThrow(() -> new WriterNotFoundException(userId));

        Article article = Article.builder()
                .title(requestDTO.getTitle())
                .content(requestDTO.getContent())
                .price(requestDTO.getPrice())
                .quantity(requestDTO.getQuantity())
                .addr1(requestDTO.getAddr1())
                .addr2(requestDTO.getAddr2())
                .category(requestDTO.getCategory())
                .tradeMethod(requestDTO.getTradeMethod())
                .tradeStatus(requestDTO.getTradeStatus())
                .viewCount(0L)
                .likeCount(0L)
                .createdDate(LocalDateTime.now())
                .modifiedDate(LocalDateTime.now())
                .writer(writer)
                .build();

        Article savedArticle = articleRepository.save(article);

        List<Map<String, String>> imageUrlMaps = imageUploadService.uploadImages(images);
        for (int i = 0; i < imageUrlMaps.size(); i++) {
            Map<String, String> imageUrlMap = imageUrlMaps.get(i);
            ProductImage.ProductImageBuilder productImageBuilder = ProductImage.builder()
                    .imageUrl(imageUrlMap.get("original"))
                    .article(savedArticle)
                    .createdDate(LocalDateTime.now())
                    .modifiedDate(LocalDateTime.now());

            // 첫 번째 이미지인 경우에만 썸네일 URL 설정
            if (i == 0 && imageUrlMap.containsKey("thumbnail")) {
                productImageBuilder.thumbnailUrl(imageUrlMap.get("thumbnail"));
            }

            ProductImage productImage = productImageBuilder.build();
            productImageRepository.save(productImage);
        }

        return ArticleResponse.from(savedArticle);
    }


    @Transactional
    public ArticleResponse updateArticle(Long id, ArticleRequest requestDTO, MultipartFile[] images) throws IOException {
        CustomUserDetails principal = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = principal.getUser().getId();

        Article article = articleRepository.findById(id).orElseThrow(() -> new ArticleNotExistException(id));

        if (!article.getWriter().getId().equals(userId)) {
            throw new ArticleNoPermitException(userId);
        }

        Article updatedArticle = Article.builder()
                .id(id)
                .title(requestDTO.getTitle() != null ? requestDTO.getTitle() : article.getTitle())
                .content(requestDTO.getContent() != null ? requestDTO.getContent() : article.getContent())
                .price(requestDTO.getPrice() != null ? requestDTO.getPrice() : article.getPrice())
                .quantity(requestDTO.getQuantity() != null ? requestDTO.getQuantity() : article.getQuantity())
                .addr1(requestDTO.getAddr1() != null ? requestDTO.getAddr1() : article.getAddr1())
                .addr2(requestDTO.getAddr2() != null ? requestDTO.getAddr2() : article.getAddr2())
                .category(requestDTO.getCategory() != null ? requestDTO.getCategory() : article.getCategory())
                .tradeMethod(requestDTO.getTradeMethod() != null ? requestDTO.getTradeMethod() : article.getTradeMethod())
                .tradeStatus(requestDTO.getTradeStatus() != null ? requestDTO.getTradeStatus() : article.getTradeStatus())
                .viewCount(article.getViewCount())
                .likeCount(article.getLikeCount())
                .createdDate(article.getCreatedDate())
                .modifiedDate(LocalDateTime.now())
                .writer(article.getWriter())
                .build();
        articleRepository.save(updatedArticle);

        List<ProductImage> existingImages = productImageRepository.findByArticle(article);
        List<String> existingImageUrls = existingImages.stream()
                .map(ProductImage::getImageUrl)
                .collect(Collectors.toList());

        List<String> newImageUrls = requestDTO.getImageUrls();

        // 삭제된 이미지 URL 확인 및 삭제 처리
        List<String> deletedImageUrls = existingImageUrls.stream()
                .filter(url -> !newImageUrls.contains(url))
                .collect(Collectors.toList());

        for (String deletedImageUrl : deletedImageUrls) {
            imageUploadService.deleteImage(deletedImageUrl);
            ProductImage productImage = productImageRepository.findByImageUrl(deletedImageUrl);
            if (productImage != null) {
                productImageRepository.delete(productImage);
            }
        }

        // 새로 추가된 이미지 파일 업로드 및 저장 처리
        if (images != null && images.length > 0) {
            List<Map<String, String>> imageUrlMaps = imageUploadService.uploadImages(images);
            for (int i = 0; i < imageUrlMaps.size(); i++) {
                Map<String, String> imageUrlMap = imageUrlMaps.get(i);
                ProductImage.ProductImageBuilder productImageBuilder = ProductImage.builder()
                        .imageUrl(imageUrlMap.get("original"))
                        .article(updatedArticle)
                        .createdDate(LocalDateTime.now())
                        .modifiedDate(LocalDateTime.now());

                // 첫 번째 이미지인 경우에만 썸네일 URL 설정
                if (i == 0 && imageUrlMap.containsKey("thumbnail")) {
                    productImageBuilder.thumbnailUrl(imageUrlMap.get("thumbnail"));
                }

                ProductImage productImage = productImageBuilder.build();
                productImageRepository.save(productImage);
            }
        }

        return ArticleResponse.from(updatedArticle);
    }

    @Transactional
    public void deleteArticle(Long id) {
        log.info("현재 게시글은 id : " + id + "임");
        Article article = articleRepository.findById(id).orElseThrow(() -> new ArticleNotExistException(id));
        CustomUserDetails principal = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = principal.getUser().getId();

        if (!article.getWriter().getId().equals(userId)) {
            throw new ArticleNoPermitException(userId);
        }

        log.info("1번 위치");
        // likeArticle 테이블에서 관련된 행 삭제
        likeArticleRepository.deleteByArticleId(id);
        log.info("2번 위치");
        // offer 테이블에서 관련된 행 삭제
        offerRepository.deleteByArticleId(id);
        log.info("3번 위치");
        chatRoomRepository.deleteByArticleId(article);



        List<ProductImage> images = productImageRepository.findByArticle(article);
        for (ProductImage productImage : images) {
            imageUploadService.deleteImage(productImage.getImageUrl());
            if (productImage.getThumbnailUrl() != null) {
                imageUploadService.deleteImage(productImage.getThumbnailUrl());
            }
        }
        productImageRepository.deleteByArticle(article);  // DB에서 이미지 레코드 삭제
        articleRepository.delete(article);  // 게시글 삭제
    }

    public ArticleDetailResponse getArticleDetail(Long id) {
        Article article = articleRepository.findById(id).orElseThrow(() -> new ArticleNotExistException(id));

        List<ProductImage> images = productImageRepository.findByArticle(article);
        String[] imageUrls = images.stream().map(ProductImage::getImageUrl).toArray(String[]::new);
        List<OfferResponse> offers = offerService.getOffersByArticleId(id);
        offers = offers.stream()
                .sorted(Comparator.comparing(OfferResponse::getOfferPrice))
                .collect(Collectors.toList());
        boolean liked = false;
        if (SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof CustomUserDetails customUserDetails) {
            Long userId = customUserDetails.getUser().getId();
            liked = likeArticleRepository.findByArticleIdAndUserId(id, userId).isPresent();
        }

        return new ArticleDetailResponse(
                article.getId(),
                article.getTitle(),
                article.getContent(),
                article.getPrice(),
                article.getQuantity(),
                article.getAddr1(),
                article.getAddr2(),
                article.getCreatedDate(),
                article.getCategory(),
                article.getTradeMethod(),
                article.getTradeStatus(),
                article.getWriter().getId(),
                article.getWriter().getUsername(),
                article.getWriter().getProfileImageUrl(),
                article.getProductImages().get(0).getThumbnailUrl(),
                imageUrls,
                liked,
                article.getLikeCount(),
                offers
        );
    }

    public List<ArticleSummaryResponse> getAllArticles() {
        List<Article> articles = articleRepository.findAll();

        return articles.stream()
                .map(article -> new ArticleSummaryResponse(
                        article.getId(),
                        article.getTitle(),
                        article.getCategory(),
                        article.getPrice(),
                        article.getTradeMethod(),
                        article.getTradeStatus(),
                        article.getCreatedDate(),
                        article.getWriter().getId()
                )).collect(Collectors.toList());
    }

    @Transactional
    public String likeArticle(Long articleId) {
        var principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = null;
        if (principal instanceof CustomUserDetails customUserDetails) {
            userId = customUserDetails.getUser().getId();
        }else if(principal instanceof String) {
            throw new NotLoggedInException("찜하려면 로그인 하세요");
        }

        userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("사용자가 존재하지 않습니다."));

        Optional<LikeArticle> likeArticles = likeArticleRepository.findByArticleIdAndUserId(articleId, userId);
        Optional<Article> article = articleRepository.findById(articleId);
        if(article.isEmpty()) throw new IllegalArgumentException("게시글이 존재하지 않습니다.");
        if(article.get().getWriter().getId().equals(userId)) throw new ForbiddenException("본인의 게시글는 찜할 수 없습니다.");

        if(likeArticles.isPresent()){
            likeArticleRepository.deleteByArticleIdAndUserId(articleId, userId);
            article.get().minusLikeCount();
            return "찜한 상품을 목록에서 제거했습니다.";
        }else{
            LikeArticle likeArticle = LikeArticle.builder()
                    .user(User.builder().id(userId).build())
                    .article(Article.builder().id(articleId).build())
                    .build();

            article.get().plusLikeCount();
            likeArticleRepository.save(likeArticle);
            return "상품을 찜목록에 등록했습니다.";
        }
    }

    @Transactional
    public void plusViewCount(Long articleId) {
        Article article = articleRepository.findById(articleId).orElseThrow(() -> new ArticleNotExistException(articleId));
        article.plusViewCount();
    }
}