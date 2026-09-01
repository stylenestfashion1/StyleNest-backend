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

import com.stylenest.stylenest_backend.dto.product.ProductResponse;
import com.stylenest.stylenest_backend.entity.Category;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.exception.ProductHasOrderHistoryException;
import com.stylenest.stylenest_backend.mapper.ProductMapper;
import com.stylenest.stylenest_backend.repository.CartItemRepository;
import com.stylenest.stylenest_backend.repository.CategoryRepository;
import com.stylenest.stylenest_backend.repository.OrderItemRepository;
import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.repository.WishlistItemRepository;
import com.stylenest.stylenest_backend.repository.projection.ProductSearchMetaProjection;
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
                orderItemRepository, cartItemRepository, wishlistItemRepository);
    }

    @Test
    void getAllProducts_populatesThumbnailAndColors() {

        newService();

        Product withImage = product(1L, "Red Dress");
        Product withoutImage = product(2L, "Blue Skirt");

        when(productRepository.findAll()).thenReturn(List.of(withImage, withoutImage));
        when(productRepository.findProductSearchMetaByProductIds(List.of(1L, 2L), null, null))
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
        when(productRepository.findProductSearchMetaByProductIds(List.of(7L), null, null))
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
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void searchProducts_whenNoVariants_returnsEmptyColorsListNotNull() {

        newService();

        Product noVariants = product(9L, "No Variant Product");

        when(productRepository.findAll()).thenReturn(List.of(noVariants));
        when(productRepository.findProductSearchMetaByProductIds(List.of(9L), null, null))
                .thenReturn(List.of());

        List<ProductResponse> responses = productService.getAllProducts();

        assertThat(responses.get(0).getAvailableColors()).isNotNull().isEmpty();
        assertThat(responses.get(0).getThumbnailUrl()).isNull();
    }

    @Test
    void deleteProduct_withNoOrderHistory_cleansCartAndWishlistThenDeletes() {

        newService();

        Product product = product(5L, "No Order Product");

        when(productRepository.findById(5L)).thenReturn(java.util.Optional.of(product));
        when(orderItemRepository.existsByProductVariant_Product_Id(5L)).thenReturn(false);

        productService.deleteProduct(5L);

        verify(cartItemRepository, times(1)).deleteByProductVariant_Product_Id(5L);
        verify(wishlistItemRepository, times(1)).deleteByProductId(5L);
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
}
