package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stylenest.stylenest_backend.dto.product.ProductJeansCodeResponse;
import com.stylenest.stylenest_backend.dto.product.ProductRequest;
import com.stylenest.stylenest_backend.dto.product.ProductResponse;
import com.stylenest.stylenest_backend.entity.Category;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.exception.DuplicateResourceException;
import com.stylenest.stylenest_backend.exception.ProductHasOrderHistoryException;
import com.stylenest.stylenest_backend.mapper.ProductMapper;
import com.stylenest.stylenest_backend.repository.CartItemRepository;
import com.stylenest.stylenest_backend.repository.CategoryRepository;
import com.stylenest.stylenest_backend.repository.OrderItemRepository;
import com.stylenest.stylenest_backend.repository.ProductImageRepository;
import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.repository.WishlistItemRepository;
import com.stylenest.stylenest_backend.repository.projection.ProductSearchMetaProjection;
import com.stylenest.stylenest_backend.service.ImageStorageService;
import com.stylenest.stylenest_backend.service.ProductThumbnailResolver;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private ImageStorageService imageStorageService;

    private ProductServiceImpl productService;

    private final ProductMapper productMapper = new ProductMapper();

    private record MetaRow(Long productId, String thumbnailUrl, String color)
            implements ProductSearchMetaProjection {

        @Override
        public Long getProductId() {
            return productId;
        }

        @Override
        public String getThumbnailUrl() {
            return thumbnailUrl;
        }

        @Override
        public String getColor() {
            return color;
        }
    }

    private Product product(Long id, String name) {

        Category category = Category.builder().gender(com.stylenest.stylenest_backend.enums.Gender.WOMEN).id(1L).name("Dresses").build();

        return Product.builder()
                .id(id)
                .name(name)
                .slug(name.toLowerCase())
                .price(BigDecimal.valueOf(999))
                .category(category)
                .build();
    }

    private void newService() {
        productService = new ProductServiceImpl(
                productRepository, categoryRepository, productMapper,
                new ProductThumbnailResolver(productRepository),
                orderItemRepository, cartItemRepository, wishlistItemRepository,
                productImageRepository, imageStorageService);
    }

    @Test
    void getAllProducts_populatesThumbnailAndColors() {

        newService();

        Product withImage = product(1L, "Red Dress");
        Product withoutImage = product(2L, "Blue Skirt");

        when(productRepository.findAll()).thenReturn(List.of(withImage, withoutImage));
        when(productRepository.findProductSearchMetaByProductIds(List.of(1L, 2L), null))
                .thenReturn(List.of(
                        // Two variant rows for the same product/thumbnail --
                        // the query now returns one row per variant rather
                        // than a pre-aggregated CSV (see ProductRepository).
                        new MetaRow(1L, "https://cdn.example.com/red-dress-1.jpg", "RED"),
                        new MetaRow(1L, "https://cdn.example.com/red-dress-1.jpg", "BLACK")));

        List<ProductResponse> responses = productService.getAllProducts();

        ProductResponse withImageResponse = responses.stream()
                .filter(r -> r.getId().equals(1L)).findFirst().orElseThrow();
        ProductResponse withoutImageResponse = responses.stream()
                .filter(r -> r.getId().equals(2L)).findFirst().orElseThrow();

        assertThat(withImageResponse.getThumbnailUrl())
                .isEqualTo("https://cdn.example.com/red-dress-1.jpg");
        assertThat(withImageResponse.getAvailableColors()).containsExactlyInAnyOrder("RED", "BLACK");

        assertThat(withoutImageResponse.getThumbnailUrl()).isNull();
        assertThat(withoutImageResponse.getAvailableColors()).isEmpty();
    }

    @Test
    void getProductById_populatesThumbnailUrl() {

        newService();

        Product p = product(7L, "Green Top");

        when(productRepository.findById(7L)).thenReturn(java.util.Optional.of(p));
        when(productRepository.findProductSearchMetaByProductIds(List.of(7L), null))
                .thenReturn(List.of(new MetaRow(7L, "https://cdn.example.com/green-top.jpg", "GREEN")));

        ProductResponse response = productService.getProductById(7L);

        assertThat(response.getThumbnailUrl()).isEqualTo("https://cdn.example.com/green-top.jpg");
        assertThat(response.getAvailableColors()).containsExactly("GREEN");
    }

    @Test
    void getAllProducts_whenNoProducts_doesNotCallMetaQuery() {

        newService();

        when(productRepository.findAll()).thenReturn(List.of());

        List<ProductResponse> responses = productService.getAllProducts();

        assertThat(responses).isEmpty();
        org.mockito.Mockito.verify(productRepository, org.mockito.Mockito.never())
                .findProductSearchMetaByProductIds(
                        org.mockito.ArgumentMatchers.anyList(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void searchProducts_whenNoVariants_returnsEmptyColorsListNotNull() {

        newService();

        Product noVariants = product(9L, "No Variant Product");

        when(productRepository.findAll()).thenReturn(List.of(noVariants));
        when(productRepository.findProductSearchMetaByProductIds(List.of(9L), null))
                .thenReturn(List.of());

        List<ProductResponse> responses = productService.getAllProducts();

        assertThat(responses.get(0).getAvailableColors()).isNotNull().isEmpty();
        assertThat(responses.get(0).getThumbnailUrl()).isNull();
    }

    @Test
    void createProduct_generatesDeterministicSkuPrefixFromName() {

        newService();

        Category category = Category.builder().id(1L).name("Tees")
                .gender(com.stylenest.stylenest_backend.enums.Gender.MEN).build();

        ProductRequest request = ProductRequest.builder()
                .name("Urban Graphic Tee")
                .price(BigDecimal.valueOf(999))
                .categoryId(1L)
                .featured(false)
                .trending(false)
                .active(true)
                .build();

        when(productRepository.existsBySlug("urban-graphic-tee")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(java.util.Optional.of(category));
        when(productRepository.findAllSkus()).thenReturn(List.of());
        when(productRepository.save(org.mockito.ArgumentMatchers.any(Product.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.createProduct(request);

        assertThat(response.getSku()).isEqualTo("UGT");
    }

    @Test
    void createProduct_whenPrefixCollides_generatesUniqueAlternate() {

        newService();

        Category category = Category.builder().id(1L).name("Tees")
                .gender(com.stylenest.stylenest_backend.enums.Gender.MEN).build();

        ProductRequest request = ProductRequest.builder()
                .name("Urban Graphic Tee")
                .price(BigDecimal.valueOf(999))
                .categoryId(1L)
                .featured(false)
                .trending(false)
                .active(true)
                .build();

        when(productRepository.existsBySlug("urban-graphic-tee")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(java.util.Optional.of(category));
        when(productRepository.findAllSkus()).thenReturn(List.of("UGT"));
        when(productRepository.save(org.mockito.ArgumentMatchers.any(Product.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.createProduct(request);

        assertThat(response.getSku()).isNotEqualTo("UGT");
        assertThat(response.getSku()).startsWith("U");
    }

    @Test
    void deleteProduct_withNoOrderHistory_cleansCartAndWishlistThenDeletes() {

        newService();

        Product product = product(5L, "No Order Product");

        when(productRepository.findById(5L)).thenReturn(java.util.Optional.of(product));
        when(orderItemRepository.existsByProductVariant_Product_Id(5L)).thenReturn(false);
        when(productImageRepository.findByProductIdOrderByColorAscDisplayOrderAsc(5L)).thenReturn(List.of());

        productService.deleteProduct(5L);

        verify(cartItemRepository, times(1)).deleteByProductVariant_Product_Id(5L);
        verify(wishlistItemRepository, times(1)).deleteByProductId(5L);
        verify(productImageRepository, times(1)).deleteAll(List.of());
        verify(productRepository, times(1)).delete(product);
    }

    @Test
    void deleteProduct_withOrderHistory_throwsAndDoesNotDeleteAnything() {

        newService();

        Product product = product(6L, "Ordered Product");

        when(productRepository.findById(6L)).thenReturn(java.util.Optional.of(product));
        when(orderItemRepository.existsByProductVariant_Product_Id(6L)).thenReturn(true);

        assertThatThrownBy(() -> productService.deleteProduct(6L))
                .isInstanceOf(ProductHasOrderHistoryException.class)
                .hasMessageContaining("active:false");

        verify(cartItemRepository, never()).deleteByProductVariant_Product_Id(org.mockito.ArgumentMatchers.anyLong());
        verify(wishlistItemRepository, never()).deleteByProductId(org.mockito.ArgumentMatchers.anyLong());
        verify(productRepository, never()).delete(org.mockito.ArgumentMatchers.any(Product.class));
    }

    @Test
    void createProduct_withJeansCode_trimsAndSaves() {

        newService();

        Category category = Category.builder().id(1L).name("Jeans")
                .gender(com.stylenest.stylenest_backend.enums.Gender.WOMEN).build();

        ProductRequest request = ProductRequest.builder()
                .name("Classic Wide-Leg Jeans")
                .price(BigDecimal.valueOf(1999))
                .categoryId(1L)
                .jeansCode("  DEN-042  ")
                .featured(false)
                .trending(false)
                .active(true)
                .build();

        when(productRepository.existsBySlug("classic-wide-leg-jeans")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(java.util.Optional.of(category));
        when(productRepository.existsByJeansCodeIgnoreCase("DEN-042")).thenReturn(false);
        when(productRepository.findAllSkus()).thenReturn(List.of());
        when(productRepository.save(org.mockito.ArgumentMatchers.any(Product.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        productService.createProduct(request);

        org.mockito.ArgumentCaptor<Product> captor = org.mockito.ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getJeansCode()).isEqualTo("DEN-042");
    }

    @Test
    void createProduct_withBlankJeansCode_savesNullNotEmptyString() {

        newService();

        Category category = Category.builder().id(1L).name("Jeans")
                .gender(com.stylenest.stylenest_backend.enums.Gender.WOMEN).build();

        ProductRequest request = ProductRequest.builder()
                .name("Plain Jeans")
                .price(BigDecimal.valueOf(1999))
                .categoryId(1L)
                .jeansCode("   ")
                .featured(false)
                .trending(false)
                .active(true)
                .build();

        when(productRepository.existsBySlug("plain-jeans")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(java.util.Optional.of(category));
        when(productRepository.findAllSkus()).thenReturn(List.of());
        when(productRepository.save(org.mockito.ArgumentMatchers.any(Product.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        productService.createProduct(request);

        org.mockito.ArgumentCaptor<Product> captor = org.mockito.ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getJeansCode()).isNull();
        verify(productRepository, never()).existsByJeansCodeIgnoreCase(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void createProduct_withDuplicateJeansCode_throwsAndDoesNotSave() {

        newService();

        Category category = Category.builder().id(1L).name("Jeans")
                .gender(com.stylenest.stylenest_backend.enums.Gender.WOMEN).build();

        ProductRequest request = ProductRequest.builder()
                .name("Another Pair")
                .price(BigDecimal.valueOf(1999))
                .categoryId(1L)
                .jeansCode("DEN-042")
                .featured(false)
                .trending(false)
                .active(true)
                .build();

        when(productRepository.existsBySlug("another-pair")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(java.util.Optional.of(category));
        when(productRepository.existsByJeansCodeIgnoreCase("DEN-042")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("DEN-042");

        verify(productRepository, never()).save(org.mockito.ArgumentMatchers.any(Product.class));
    }

    @Test
    void updateProduct_withDuplicateJeansCode_excludesOwnProductButRejectsOthers() {

        newService();

        Product existing = product(11L, "Existing Jeans");
        Category category = existing.getCategory();

        ProductRequest request = ProductRequest.builder()
                .name("Existing Jeans")
                .price(BigDecimal.valueOf(1999))
                .categoryId(category.getId())
                .jeansCode("DEN-099")
                .featured(false)
                .trending(false)
                .active(true)
                .build();

        when(productRepository.findById(11L)).thenReturn(java.util.Optional.of(existing));
        when(categoryRepository.findById(category.getId())).thenReturn(java.util.Optional.of(category));
        when(productRepository.existsByJeansCodeIgnoreCaseAndIdNot("DEN-099", 11L)).thenReturn(true);

        assertThatThrownBy(() -> productService.updateProduct(11L, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("DEN-099");

        verify(productRepository, never()).save(org.mockito.ArgumentMatchers.any(Product.class));
    }

    @Test
    void getJeansCode_delegatesToRepository() {

        newService();

        when(productRepository.findJeansCodeByProductId(3L))
                .thenReturn(java.util.Optional.of(new ProductJeansCodeResponse(3L, "DEN-003")));

        ProductJeansCodeResponse response = productService.getJeansCode(3L);

        assertThat(response.getJeansCode()).isEqualTo("DEN-003");
    }

    @Test
    void productResponse_hasNoJeansCodeField_staysOffThePublicDto() {

        // Regression guard: ProductResponse is shared by the public,
        // unauthenticated GET /api/products/** (see ProductController) --
        // if a jeansCode field is ever added here by mistake, this fails
        // loudly instead of silently leaking it to customers.
        assertThatThrownBy(() -> ProductResponse.class.getDeclaredField("jeansCode"))
                .isInstanceOf(NoSuchFieldException.class);
    }
}
