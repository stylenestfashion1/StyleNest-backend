package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.stylenest.stylenest_backend.dto.product.ProductVariantRequest;
import com.stylenest.stylenest_backend.dto.product.ProductVariantResponse;
import com.stylenest.stylenest_backend.dto.product.RenameColorGroupRequest;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductImage;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.enums.Size;
import com.stylenest.stylenest_backend.exception.DuplicateResourceException;
import com.stylenest.stylenest_backend.mapper.ProductImageMapper;
import com.stylenest.stylenest_backend.mapper.ProductVariantMapper;
import com.stylenest.stylenest_backend.repository.ProductImageRepository;
import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.repository.ProductVariantRepository;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private ProductImageRepository imageRepository;

    private ProductVariantServiceImpl variantService;

    private final ProductVariantMapper variantMapper = new ProductVariantMapper();
    private final ProductImageMapper imageMapper = new ProductImageMapper();

    private void newService() {
        variantService = new ProductVariantServiceImpl(
                productRepository, variantRepository, imageRepository, variantMapper, imageMapper);
    }

    private Product product(Long id, String sku) {
        return Product.builder().id(id).name("Test Product").sku(sku).build();
    }

    private ProductVariantRequest request(String color, Size size) {
        return ProductVariantRequest.builder().color(color).size(size).stock(10).build();
    }

    @Test
    void createVariant_firstColorOnProduct_generatesFreshSkuFromProductPrefix() {

        newService();

        Product product = product(1L, "UGT");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(variantRepository.existsByProductAndColorAndSize(product, "BLACK", Size.M)).thenReturn(false);
        when(variantRepository.findByProduct(product)).thenReturn(List.of());
        when(imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(1L, "BLACK")).thenReturn(List.of());
        when(variantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductVariantResponse response = variantService.createVariant(1L, request("BLACK", Size.M));

        assertThat(response.getSku()).isEqualTo("STN-UGT-BLA-M");
    }

    @Test
    void createVariant_secondSizeOfExistingColor_reusesSameColorCode() {

        newService();

        Product product = product(1L, "UGT");

        ProductVariant existingSameColor = ProductVariant.builder()
                .id(10L).product(product).color("BLACK").size(Size.S).stock(10).sku("STN-UGT-BLA-S").build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(variantRepository.existsByProductAndColorAndSize(product, "BLACK", Size.M)).thenReturn(false);
        when(variantRepository.findByProduct(product)).thenReturn(List.of(existingSameColor));
        when(imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(1L, "BLACK")).thenReturn(List.of());
        when(variantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductVariantResponse response = variantService.createVariant(1L, request("BLACK", Size.M));

        assertThat(response.getSku()).isEqualTo("STN-UGT-BLA-M");
    }

    @Test
    void createVariant_newColorOnProductWithExistingColor_getsDistinctCode() {

        newService();

        Product product = product(1L, "UGT");

        ProductVariant existingBlack = ProductVariant.builder()
                .id(10L).product(product).color("BLACK").size(Size.M).stock(10).sku("STN-UGT-BLA-M").build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(variantRepository.existsByProductAndColorAndSize(product, "BLUE", Size.M)).thenReturn(false);
        when(variantRepository.findByProduct(product)).thenReturn(List.of(existingBlack));
        when(imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(1L, "BLUE")).thenReturn(List.of());
        when(variantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductVariantResponse response = variantService.createVariant(1L, request("BLUE", Size.M));

        assertThat(response.getSku()).isEqualTo("STN-UGT-BLU-M");
    }

    @Test
    void createVariant_whenNaturalSkuCollidesWithBadHistoricalData_recoversWithAlternateSku() {

        // Reproduces the 2026-09 incident: a BLUE variant's stored SKU had picked up FADED
        // BLUE's color code ("FBL" instead of "BLU") from bad historical data, so looking up
        // BLUE's "existing" code returned "FBL" -- and creating BLUE+XXL then computed the
        // exact same SKU as FADED BLUE's real XXL variant, which used to fail the INSERT with
        // an unhandled unique-constraint violation (the "Something went wrong" bug). The fix
        // must detect the collision via existsBySku and mint a different, unique SKU instead
        // of ever attempting to insert a duplicate.

        newService();

        Product product = product(1L, "FBB");

        ProductVariant corruptedBlueXs = ProductVariant.builder()
                .id(379L).product(product).color("BLUE").size(Size.XS).stock(10).sku("STN-FBB-FBL-XS").build();
        ProductVariant fadedBlueXxl = ProductVariant.builder()
                .id(383L).product(product).color("FADED BLUE").size(Size.XXL).stock(10).sku("STN-FBB-FBL-XXL").build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(variantRepository.existsByProductAndColorAndSize(product, "BLUE", Size.XXL)).thenReturn(false);
        when(variantRepository.findByProduct(product)).thenReturn(List.of(corruptedBlueXs, fadedBlueXxl));
        when(imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(1L, "BLUE")).thenReturn(List.of());
        when(variantRepository.existsBySku("STN-FBB-FBL-XXL")).thenReturn(true);
        when(variantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductVariantResponse response = variantService.createVariant(1L, request("BLUE", Size.XXL));

        assertThat(response.getSku()).isNotEqualTo("STN-FBB-FBL-XXL");
        assertThat(response.getSku()).startsWith("STN-FBB-");
        assertThat(response.getSku()).endsWith("-XXL");
    }

    @Test
    void updateVariant_doesNotRegenerateSku() {

        newService();

        Product product = product(1L, "UGT");

        ProductVariant existing = ProductVariant.builder()
                .id(10L).product(product).color("BLACK").size(Size.M).stock(10).sku("STN-UGT-BLA-M").build();

        when(variantRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(1L, "BLACK")).thenReturn(List.of());
        when(variantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductVariantRequest updateRequest = request("BLACK", Size.M);
        updateRequest.setStock(25);

        ProductVariantResponse response = variantService.updateVariant(10L, updateRequest);

        assertThat(response.getSku()).isEqualTo("STN-UGT-BLA-M");
        assertThat(response.getStock()).isEqualTo(25);
    }

    // -- renameColorGroup (color-group level edit, replacing the old per-size color update flow) --

    private ProductVariant variant(Long id, Product product, String color, Size size, Integer stock, String sku) {
        return ProductVariant.builder().id(id).product(product).color(color).size(size).stock(stock).sku(sku).build();
    }

    @Test
    void renameColorGroup_renamesEveryExistingSizeInOneOperation() {

        newService();

        Product product = product(1L, "UGT");
        List<ProductVariant> blackGroup = List.of(
                variant(10L, product, "BLACK", Size.S, 40, "STN-UGT-BLA-S"),
                variant(11L, product, "BLACK", Size.M, 40, "STN-UGT-BLA-M"),
                variant(12L, product, "BLACK", Size.XL, 40, "STN-UGT-BLA-XL"));

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(variantRepository.findByProductAndColor(product, "BLACK")).thenReturn(blackGroup);
        when(variantRepository.existsByProductAndColor(product, "BABY PINK")).thenReturn(false);
        when(variantRepository.findByProduct(product)).thenReturn(blackGroup);
        when(imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(1L, "BLACK")).thenReturn(List.of());
        when(imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(1L, "BABY PINK")).thenReturn(List.of());
        when(variantRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ProductVariantResponse> response = variantService.renameColorGroup(
                1L, "BLACK", RenameColorGroupRequest.builder().newColor("Baby Pink").build());

        assertThat(response).hasSize(3);
        assertThat(response).allSatisfy(v -> assertThat(v.getColor()).isEqualTo("BABY PINK"));
        // Same variant ids, sizes and stock preserved -- nothing was deleted/recreated.
        assertThat(response).extracting(ProductVariantResponse::getId).containsExactlyInAnyOrder(10L, 11L, 12L);
        assertThat(response).extracting(ProductVariantResponse::getSize)
                .containsExactlyInAnyOrder(Size.S, Size.M, Size.XL);
        assertThat(response).extracting(ProductVariantResponse::getStock).containsOnly(40);
        assertThat(response).allSatisfy(v -> assertThat(v.getSku()).startsWith("STN-UGT-").endsWith("-" + v.getSize().name()));
    }

    @Test
    void renameColorGroup_doesNotCreateMissingSizes() {

        newService();

        Product product = product(1L, "UGT");
        // Only 2 sizes exist for this color -- L and XXL are deliberately absent.
        List<ProductVariant> blueGroup = List.of(
                variant(20L, product, "BLUE", Size.M, 40, "STN-UGT-BLU-M"),
                variant(21L, product, "BLUE", Size.XL, 40, "STN-UGT-BLU-XL"));

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(variantRepository.findByProductAndColor(product, "BLUE")).thenReturn(blueGroup);
        when(variantRepository.existsByProductAndColor(product, "NAVY BLUE")).thenReturn(false);
        when(variantRepository.findByProduct(product)).thenReturn(blueGroup);
        when(imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(1L, "BLUE")).thenReturn(List.of());
        when(imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(1L, "NAVY BLUE")).thenReturn(List.of());
        when(variantRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ProductVariantResponse> response = variantService.renameColorGroup(
                1L, "BLUE", RenameColorGroupRequest.builder().newColor("Navy Blue").build());

        assertThat(response).hasSize(2);
        assertThat(response).extracting(ProductVariantResponse::getSize)
                .containsExactlyInAnyOrder(Size.M, Size.XL);
    }

    @Test
    void renameColorGroup_onlyTouchesThatColorGroup_notOtherColorsOnSameProduct() {

        newService();

        Product product = product(1L, "UGT");
        ProductVariant black = variant(10L, product, "BLACK", Size.M, 40, "STN-UGT-BLA-M");
        ProductVariant blue = variant(20L, product, "BLUE", Size.M, 40, "STN-UGT-BLU-M");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(variantRepository.findByProductAndColor(product, "BLACK")).thenReturn(List.of(black));
        when(variantRepository.existsByProductAndColor(product, "BABY PINK")).thenReturn(false);
        when(variantRepository.findByProduct(product)).thenReturn(List.of(black, blue));
        when(imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(1L, "BLACK")).thenReturn(List.of());
        when(imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(1L, "BABY PINK")).thenReturn(List.of());
        when(variantRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        variantService.renameColorGroup(1L, "BLACK", RenameColorGroupRequest.builder().newColor("Baby Pink").build());

        // BLUE's own color/sku were never touched.
        assertThat(blue.getColor()).isEqualTo("BLUE");
        assertThat(blue.getSku()).isEqualTo("STN-UGT-BLU-M");
        // Only the BLACK group's images were ever queried/relabeled -- BLUE's images query never happened.
        verify(imageRepository, never()).findByProductIdAndColorOrderByDisplayOrderAsc(1L, "BLUE");
    }

    @Test
    void renameColorGroup_whenNewColorAlreadyExistsOnProduct_throwsDuplicateError_doesNotMerge() {

        newService();

        Product product = product(1L, "UGT");
        ProductVariant black = variant(10L, product, "BLACK", Size.M, 40, "STN-UGT-BLA-M");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(variantRepository.findByProductAndColor(product, "BLACK")).thenReturn(List.of(black));
        when(variantRepository.existsByProductAndColor(product, "BABY PINK")).thenReturn(true);

        assertThatThrownBy(() ->
                variantService.renameColorGroup(1L, "BLACK", RenameColorGroupRequest.builder().newColor("Baby Pink").build()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Baby Pink")
                .hasMessageContaining("already exists");

        // Nothing was saved -- no partial/silent merge.
        verify(variantRepository, never()).saveAll(any());
        assertThat(black.getColor()).isEqualTo("BLACK");
    }

    @Test
    void renameColorGroup_imagesFollowRename_othersColorImagesUntouched() {

        newService();

        Product product = product(1L, "UGT");
        ProductVariant black = variant(10L, product, "BLACK", Size.M, 40, "STN-UGT-BLA-M");
        ProductImage blackImage = ProductImage.builder().id(100L).product(product).color("BLACK").displayOrder(1)
                .imageUrl("https://cdn/black.jpg").build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(variantRepository.findByProductAndColor(product, "BLACK")).thenReturn(List.of(black));
        when(variantRepository.existsByProductAndColor(product, "BABY PINK")).thenReturn(false);
        when(variantRepository.findByProduct(product)).thenReturn(List.of(black));
        when(imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(1L, "BLACK")).thenReturn(List.of(blackImage));
        when(imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(1L, "BABY PINK")).thenReturn(List.of(blackImage));
        when(variantRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(imageRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        variantService.renameColorGroup(1L, "BLACK", RenameColorGroupRequest.builder().newColor("Baby Pink").build());

        // The image row that already belonged to BLACK now follows to BABY PINK -- same image,
        // just relabeled, never swapped for a different color's picture.
        assertThat(blackImage.getColor()).isEqualTo("BABY PINK");
        assertThat(blackImage.getImageUrl()).isEqualTo("https://cdn/black.jpg");
    }

    @Test
    void renameColorGroup_normalizesCaseInsensitiveColorInput() {

        newService();

        Product product = product(1L, "UGT");
        ProductVariant black = variant(10L, product, "BLACK", Size.M, 40, "STN-UGT-BLA-M");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(variantRepository.findByProductAndColor(product, "BLACK")).thenReturn(List.of(black));
        when(variantRepository.existsByProductAndColor(product, "DUSTY BROWN")).thenReturn(false);
        when(variantRepository.findByProduct(product)).thenReturn(List.of(black));
        when(imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(1L, "BLACK")).thenReturn(List.of());
        when(imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(1L, "DUSTY BROWN")).thenReturn(List.of());
        when(variantRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        // Admin types lowercase, mixed case, etc. -- must normalize the same as everywhere else.
        List<ProductVariantResponse> response = variantService.renameColorGroup(
                1L, "black", RenameColorGroupRequest.builder().newColor("dusty brown").build());

        assertThat(response.get(0).getColor()).isEqualTo("DUSTY BROWN");
    }

    // -- Free Size / extended Palazzo sizes (3XL-7XL) SKU generation --

    @Test
    void createVariant_freeSize_usesFsTokenInSkuNotTheSpacedLabel() {

        newService();

        Product product = product(1L, "PEW");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(variantRepository.existsByProductAndColorAndSize(product, "BLACK", Size.FREE_SIZE)).thenReturn(false);
        when(variantRepository.findByProduct(product)).thenReturn(List.of());
        when(imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(1L, "BLACK")).thenReturn(List.of());
        when(variantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductVariantResponse response = variantService.createVariant(1L, request("BLACK", Size.FREE_SIZE));

        // "Free Size" (the display label) must never leak a space into the SKU.
        assertThat(response.getSku()).isEqualTo("STN-PEW-BLA-FS");
        assertThat(response.getSize()).isEqualTo(Size.FREE_SIZE);
    }

    @Test
    void createVariant_extended3xlTo7xlSizes_generateDistinctValidSkus() {

        newService();

        Product product = product(1L, "PEW");

        for (Size size : new Size[] { Size.SIZE_3XL, Size.SIZE_4XL, Size.SIZE_5XL, Size.SIZE_6XL, Size.SIZE_7XL }) {
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(variantRepository.existsByProductAndColorAndSize(product, "BLUE", size)).thenReturn(false);
            when(variantRepository.findByProduct(product)).thenReturn(List.of());
            when(imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(1L, "BLUE")).thenReturn(List.of());
            when(variantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductVariantResponse response = variantService.createVariant(1L, request("BLUE", size));

            assertThat(response.getSku()).endsWith("-" + size.getLabel());
            assertThat(response.getSize()).isEqualTo(size);
        }
    }
}
