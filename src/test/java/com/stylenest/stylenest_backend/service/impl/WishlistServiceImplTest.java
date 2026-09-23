package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.stylenest.stylenest_backend.dto.wishlist.AddToWishlistRequest;
import com.stylenest.stylenest_backend.dto.wishlist.WishlistItemResponse;
import com.stylenest.stylenest_backend.dto.wishlist.WishlistResponse;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.entity.Wishlist;
import com.stylenest.stylenest_backend.entity.WishlistItem;
import com.stylenest.stylenest_backend.enums.Size;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.mapper.WishlistMapper;
import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.repository.ProductVariantRepository;
import com.stylenest.stylenest_backend.repository.UserRepository;
import com.stylenest.stylenest_backend.repository.WishlistItemRepository;
import com.stylenest.stylenest_backend.repository.WishlistRepository;
import com.stylenest.stylenest_backend.repository.projection.ProductColorImageProjection;
import com.stylenest.stylenest_backend.repository.projection.ProductSearchMetaProjection;
import com.stylenest.stylenest_backend.service.ProductThumbnailResolver;
import com.stylenest.stylenest_backend.service.VariantImageResolver;

@ExtendWith(MockitoExtension.class)
class WishlistServiceImplTest {

    @Mock
    private WishlistRepository wishlistRepository;

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.stylenest.stylenest_backend.repository.ProductImageRepository productImageRepository;

    private WishlistServiceImpl wishlistService;

    private User user;

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

    private record ImageRow(Long productId, String color, String imageUrl) implements ProductColorImageProjection {

        @Override
        public Long getProductId() {
            return productId;
        }

        @Override
        public String getColor() {
            return color;
        }

        @Override
        public String getImageUrl() {
            return imageUrl;
        }
    }

    @BeforeEach
    void setUp() {

        wishlistService = new WishlistServiceImpl(
                wishlistRepository,
                wishlistItemRepository,
                productRepository,
                productVariantRepository,
                userRepository,
                new WishlistMapper(),
                new ProductThumbnailResolver(productRepository),
                new VariantImageResolver(productImageRepository));

        user = User.builder().id(1L).email("customer@example.com").build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of()));

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Product product(Long id, String name) {
        return Product.builder().id(id).name(name).slug(name.toLowerCase()).build();
    }

    private ProductVariant variant(Long id, Product product, String color, Size size) {
        return ProductVariant.builder().id(id).product(product).color(color).size(size).stock(5).build();
    }

    @Test
    void getWishlist_withoutVariant_fallsBackToProductThumbnail() {

        Product product = product(1L, "Rose Wrap Midi Dress");

        Wishlist wishlist = Wishlist.builder().id(9L).user(user).build();
        WishlistItem item = WishlistItem.builder().id(1L).wishlist(wishlist).product(product).build();
        wishlist.setWishlistItems(List.of(item));

        when(wishlistRepository.findByUser(user)).thenReturn(Optional.of(wishlist));
        when(productRepository.findProductSearchMetaByProductIds(List.of(1L), null))
                .thenReturn(List.of(new MetaRow(1L,
                        "https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=800&q=80", "BLACK")));

        WishlistResponse response = wishlistService.getWishlist();

        WishlistItemResponse itemResponse = response.getItems().get(0);
        assertThat(itemResponse.getImageUrl())
                .isEqualTo("https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=800&q=80");
        assertThat(itemResponse.getProductVariantId()).isNull();
        assertThat(itemResponse.getColor()).isNull();
        assertThat(itemResponse.getSize()).isNull();
    }

    @Test
    void getWishlist_withoutVariant_productHasNoThumbnailAtAll_returnsNullNotException() {

        // Regression: the product-level fallback query returning no row at
        // all for this product (no images anywhere) must not NPE when
        // extracted down to a thumbnailUrl -- null is a normal, valid value
        // here, not an error.
        Product product = product(2L, "Product With No Images");

        Wishlist wishlist = Wishlist.builder().id(9L).user(user).build();
        WishlistItem item = WishlistItem.builder().id(1L).wishlist(wishlist).product(product).build();
        wishlist.setWishlistItems(List.of(item));

        when(wishlistRepository.findByUser(user)).thenReturn(Optional.of(wishlist));
        when(productRepository.findProductSearchMetaByProductIds(List.of(2L), null))
                .thenReturn(List.of());

        WishlistResponse response = wishlistService.getWishlist();

        assertThat(response.getItems().get(0).getImageUrl()).isNull();
    }

    @Test
    void getWishlist_withStoredVariant_resolvesFromVariantOwnImageNotProductDefault() {

        Product product = product(3L, "Shoowel");
        ProductVariant redL = variant(10L, product, "RED", Size.L);

        Wishlist wishlist = Wishlist.builder().id(9L).user(user).build();
        WishlistItem item = WishlistItem.builder()
                .id(1L).wishlist(wishlist).product(product).productVariant(redL).build();
        wishlist.setWishlistItems(List.of(item));

        when(wishlistRepository.findByUser(user)).thenReturn(Optional.of(wishlist));
        when(productImageRepository.findFirstImageByProductIdsGroupedByColor(List.of(3L)))
                .thenReturn(List.of(new ImageRow(3L, "RED", "https://example.com/red-l.jpg")));

        WishlistResponse response = wishlistService.getWishlist();

        WishlistItemResponse itemResponse = response.getItems().get(0);
        assertThat(itemResponse.getImageUrl()).isEqualTo("https://example.com/red-l.jpg");
        assertThat(itemResponse.getProductVariantId()).isEqualTo(10L);
        assertThat(itemResponse.getColor()).isEqualTo("RED");
        assertThat(itemResponse.getSize()).isEqualTo("L");

        // Product-level thumbnail query must not be called at all for an
        // all-variant wishlist.
        org.mockito.Mockito.verifyNoInteractions(productRepository);
    }

    @Test
    void getWishlist_withMixedItems_batchesEachResolverExactlyOnce() {

        Product p1 = product(1L, "No Variant Product");
        Product p3 = product(3L, "Shoowel");
        ProductVariant redL = variant(10L, p3, "RED", Size.L);
        ProductVariant blackXs = variant(11L, p3, "BLACK", Size.XS);

        Wishlist wishlist = Wishlist.builder().id(9L).user(user).build();
        WishlistItem noVariantItem = WishlistItem.builder().id(1L).wishlist(wishlist).product(p1).build();
        WishlistItem redItem = WishlistItem.builder()
                .id(2L).wishlist(wishlist).product(p3).productVariant(redL).build();
        WishlistItem blackItem = WishlistItem.builder()
                .id(3L).wishlist(wishlist).product(p3).productVariant(blackXs).build();
        wishlist.setWishlistItems(List.of(noVariantItem, redItem, blackItem));

        when(wishlistRepository.findByUser(user)).thenReturn(Optional.of(wishlist));
        when(productRepository.findProductSearchMetaByProductIds(List.of(1L), null))
                .thenReturn(List.of(new MetaRow(1L, "https://example.com/default.jpg", "BLUE")));
        when(productImageRepository.findFirstImageByProductIdsGroupedByColor(List.of(3L)))
                .thenReturn(List.of(
                        new ImageRow(3L, "RED", "https://example.com/red-l.jpg"),
                        new ImageRow(3L, "BLACK", "https://example.com/black-xs.jpg")));

        wishlistService.getWishlist();

        verify(productRepository, times(1))
                .findProductSearchMetaByProductIds(anyList(), org.mockito.ArgumentMatchers.isNull());
        verify(productImageRepository, times(1)).findFirstImageByProductIdsGroupedByColor(anyList());
    }

    @Test
    void addToWishlist_withVariant_storesAndReturnsVariantSpecificImage() {

        Product product = product(3L, "Shoowel");
        ProductVariant redL = variant(10L, product, "RED", Size.L);

        Wishlist wishlist = Wishlist.builder()
                .id(9L).user(user).wishlistItems(new java.util.ArrayList<>()).build();

        when(wishlistRepository.findByUser(user)).thenReturn(Optional.of(wishlist));
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(redL));
        when(wishlistItemRepository.findByWishlistAndProductVariant(wishlist, redL))
                .thenReturn(Optional.empty());
        when(wishlistItemRepository.save(org.mockito.ArgumentMatchers.any(WishlistItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(productImageRepository.findFirstImageByProductIdsGroupedByColor(List.of(3L)))
                .thenReturn(List.of(new ImageRow(3L, "RED", "https://example.com/red-l.jpg")));

        WishlistResponse response = wishlistService.addToWishlist(
                AddToWishlistRequest.builder().productId(3L).productVariantId(10L).build());

        WishlistItemResponse itemResponse = response.getItems().get(0);
        assertThat(itemResponse.getProductVariantId()).isEqualTo(10L);
        assertThat(itemResponse.getImageUrl()).isEqualTo("https://example.com/red-l.jpg");
        assertThat(itemResponse.getColor()).isEqualTo("RED");
        assertThat(itemResponse.getSize()).isEqualTo("L");
    }

    @Test
    void addToWishlist_reAddWithDifferentVariant_createsSeparateItem_doesNotReplace() {

        Product product = product(3L, "Shoowel");
        ProductVariant redL = variant(10L, product, "RED", Size.L);
        ProductVariant blackXs = variant(11L, product, "BLACK", Size.XS);

        Wishlist wishlist = Wishlist.builder().id(9L).user(user).build();
        WishlistItem existingRedItem = WishlistItem.builder()
                .id(1L).wishlist(wishlist).product(product).productVariant(redL).build();
        wishlist.setWishlistItems(new java.util.ArrayList<>(List.of(existingRedItem)));

        when(wishlistRepository.findByUser(user)).thenReturn(Optional.of(wishlist));
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findById(11L)).thenReturn(Optional.of(blackXs));
        when(wishlistItemRepository.findByWishlistAndProductVariant(wishlist, blackXs))
                .thenReturn(Optional.empty());
        when(wishlistItemRepository.save(org.mockito.ArgumentMatchers.any(WishlistItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(productImageRepository.findFirstImageByProductIdsGroupedByColor(anyList()))
                .thenReturn(List.of(
                        new ImageRow(3L, "RED", "https://example.com/red-l.jpg"),
                        new ImageRow(3L, "BLACK", "https://example.com/black-xs.jpg")));

        WishlistResponse response = wishlistService.addToWishlist(
                AddToWishlistRequest.builder().productId(3L).productVariantId(11L).build());

        // Now TWO items: the original RED/L entry is untouched, plus the
        // new BLACK/XS entry.
        assertThat(response.getItems()).hasSize(2);
        assertThat(existingRedItem.getProductVariant().getId()).isEqualTo(10L);
        assertThat(response.getItems())
                .extracting(WishlistItemResponse::getProductVariantId)
                .containsExactlyInAnyOrder(10L, 11L);
        verify(wishlistItemRepository, times(1)).save(org.mockito.ArgumentMatchers.any(WishlistItem.class));
    }

    @Test
    void addToWishlist_reAddWithSameVariant_isIdempotent_noDuplicate() {

        Product product = product(3L, "Shoowel");
        ProductVariant redL = variant(10L, product, "RED", Size.L);

        Wishlist wishlist = Wishlist.builder().id(9L).user(user).build();
        WishlistItem existingItem = WishlistItem.builder()
                .id(1L).wishlist(wishlist).product(product).productVariant(redL).build();
        wishlist.setWishlistItems(List.of(existingItem));

        when(wishlistRepository.findByUser(user)).thenReturn(Optional.of(wishlist));
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(redL));
        when(wishlistItemRepository.findByWishlistAndProductVariant(wishlist, redL))
                .thenReturn(Optional.of(existingItem));
        when(productImageRepository.findFirstImageByProductIdsGroupedByColor(anyList()))
                .thenReturn(List.of(new ImageRow(3L, "RED", "https://example.com/red-l.jpg")));

        WishlistResponse response = wishlistService.addToWishlist(
                AddToWishlistRequest.builder().productId(3L).productVariantId(10L).build());

        assertThat(response.getItems()).hasSize(1);
        verify(wishlistItemRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any(WishlistItem.class));
    }

    @Test
    void addToWishlist_noVariant_reAddNoVariant_isIdempotent() {

        Product product = product(3L, "Shoowel");

        Wishlist wishlist = Wishlist.builder().id(9L).user(user).build();
        WishlistItem existingItem = WishlistItem.builder()
                .id(1L).wishlist(wishlist).product(product).build();
        wishlist.setWishlistItems(List.of(existingItem));

        when(wishlistRepository.findByUser(user)).thenReturn(Optional.of(wishlist));
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(wishlistItemRepository.findByWishlistAndProductAndProductVariantIsNull(wishlist, product))
                .thenReturn(Optional.of(existingItem));
        when(productRepository.findProductSearchMetaByProductIds(anyList(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(List.of());

        WishlistResponse response = wishlistService.addToWishlist(
                AddToWishlistRequest.builder().productId(3L).build());

        assertThat(response.getItems()).hasSize(1);
        verify(wishlistItemRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any(WishlistItem.class));
    }

    @Test
    void addToWishlist_noVariantEntryAlreadyExists_addingSpecificVariant_createsSeparateItem() {

        // Generic (no-variant) entry already saved for this product;
        // adding a specific variant must create a second, independent
        // entry rather than colliding with the generic one.
        Product product = product(3L, "Shoowel");
        ProductVariant redL = variant(10L, product, "RED", Size.L);

        Wishlist wishlist = Wishlist.builder().id(9L).user(user).build();
        WishlistItem genericItem = WishlistItem.builder()
                .id(1L).wishlist(wishlist).product(product).build();
        wishlist.setWishlistItems(new java.util.ArrayList<>(List.of(genericItem)));

        when(wishlistRepository.findByUser(user)).thenReturn(Optional.of(wishlist));
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(redL));
        when(wishlistItemRepository.findByWishlistAndProductVariant(wishlist, redL))
                .thenReturn(Optional.empty());
        when(wishlistItemRepository.save(org.mockito.ArgumentMatchers.any(WishlistItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(productImageRepository.findFirstImageByProductIdsGroupedByColor(anyList()))
                .thenReturn(List.of(new ImageRow(3L, "RED", "https://example.com/red-l.jpg")));
        when(productRepository.findProductSearchMetaByProductIds(anyList(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(List.of());

        WishlistResponse response = wishlistService.addToWishlist(
                AddToWishlistRequest.builder().productId(3L).productVariantId(10L).build());

        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems())
                .extracting(WishlistItemResponse::getProductVariantId)
                .containsExactlyInAnyOrder(null, 10L);
    }

    @Test
    void addToWishlist_variantNotBelongingToProduct_isRejected() {

        Product product = product(3L, "Shoowel");
        Product otherProduct = product(4L, "Other Product");
        ProductVariant otherProductsVariant = variant(20L, otherProduct, "RED", Size.L);

        Wishlist wishlist = Wishlist.builder()
                .id(9L).user(user).wishlistItems(new java.util.ArrayList<>()).build();

        when(wishlistRepository.findByUser(user)).thenReturn(Optional.of(wishlist));
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findById(20L)).thenReturn(Optional.of(otherProductsVariant));

        assertThatThrownBy(() -> wishlistService.addToWishlist(
                AddToWishlistRequest.builder().productId(3L).productVariantId(20L).build()))
                .isInstanceOf(BadRequestException.class);
    }
}
