package com.stylenest.stylenest_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.repository.projection.ProductSearchMetaProjection;

@ExtendWith(MockitoExtension.class)
class ProductThumbnailResolverTest {

    @Mock
    private ProductRepository productRepository;

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

    @Test
    void resolve_whenSomeProductsHaveNoThumbnail_doesNotThrow_returnsNullForThose() {

        ProductThumbnailResolver resolver = new ProductThumbnailResolver(productRepository);

        // Product 2 has no row at all (no images) -- resolveMeta defaults
        // it to ProductSearchMeta.EMPTY, whose thumbnailUrl() is null. A
        // plain Map<Long,String> must be able to hold that null value
        // rather than throwing (this previously NPE'd via Collectors.toMap).
        when(productRepository.findProductSearchMetaByProductIds(List.of(1L, 2L), null, null))
                .thenReturn(List.of(new MetaRow(1L, "https://cdn.example.com/1.jpg", "RED")));

        Map<Long, String> result = resolver.resolve(List.of(1L, 2L));

        assertThat(result.get(1L)).isEqualTo("https://cdn.example.com/1.jpg");
        assertThat(result).containsKey(2L);
        assertThat(result.get(2L)).isNull();
    }
}
