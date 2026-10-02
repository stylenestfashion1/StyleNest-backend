package com.stylenest.stylenest_backend.mapper;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.stylenest.stylenest_backend.dto.product.ProductVariantRequest;
import com.stylenest.stylenest_backend.dto.product.ProductVariantResponse;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.enums.Size;

class ProductVariantShippingMapperTest {

    private ProductVariantMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ProductVariantMapper();
    }

    @Test
    void toEntity_shouldMapShippingFields_whenProvided() {
        ProductVariantRequest request = ProductVariantRequest.builder()
                .color("BLACK")
                .colorHex("#000000")
                .size(Size.M)
                .stock(10)
                .shippingWeightGrams(new BigDecimal("350.50"))
                .packageLengthCm(new BigDecimal("30.00"))
                .packageWidthCm(new BigDecimal("20.00"))
                .packageHeightCm(new BigDecimal("5.00"))
                .build();

        ProductVariant entity = mapper.toEntity(request);

        assertThat(entity.getColor()).isEqualTo("BLACK");
        assertThat(entity.getColorHex()).isEqualTo("#000000");
        assertThat(entity.getSize()).isEqualTo(Size.M);
        assertThat(entity.getStock()).isEqualTo(10);
        assertThat(entity.getShippingWeightGrams()).isEqualByComparingTo("350.50");
        assertThat(entity.getPackageLengthCm()).isEqualByComparingTo("30.00");
        assertThat(entity.getPackageWidthCm()).isEqualByComparingTo("20.00");
        assertThat(entity.getPackageHeightCm()).isEqualByComparingTo("5.00");
    }

    @Test
    void toEntity_shouldLeaveShippingFieldsNull_whenOmitted() {
        ProductVariantRequest request = ProductVariantRequest.builder()
                .color("WHITE")
                .size(Size.L)
                .stock(5)
                .build();

        ProductVariant entity = mapper.toEntity(request);

        assertThat(entity.getShippingWeightGrams()).isNull();
        assertThat(entity.getPackageLengthCm()).isNull();
        assertThat(entity.getPackageWidthCm()).isNull();
        assertThat(entity.getPackageHeightCm()).isNull();
    }

    @Test
    void toResponse_shouldMapShippingFields_whenPresentOnEntity() {
        ProductVariant entity = ProductVariant.builder()
                .id(101L)
                .color("RED")
                .size(Size.S)
                .stock(15)
                .sku("STN-SHR-RED-S")
                .shippingWeightGrams(new BigDecimal("220.00"))
                .packageLengthCm(new BigDecimal("25.00"))
                .packageWidthCm(new BigDecimal("18.00"))
                .packageHeightCm(new BigDecimal("3.50"))
                .build();

        ProductVariantResponse response = mapper.toResponse(entity, Collections.emptyList());

        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getShippingWeightGrams()).isEqualByComparingTo("220.00");
        assertThat(response.getPackageLengthCm()).isEqualByComparingTo("25.00");
        assertThat(response.getPackageWidthCm()).isEqualByComparingTo("18.00");
        assertThat(response.getPackageHeightCm()).isEqualByComparingTo("3.50");
    }

    @Test
    void updateEntity_shouldUpdateShippingFields() {
        ProductVariant entity = ProductVariant.builder()
                .color("BLUE")
                .size(Size.XL)
                .stock(20)
                .shippingWeightGrams(new BigDecimal("400.00"))
                .packageLengthCm(new BigDecimal("32.00"))
                .packageWidthCm(new BigDecimal("22.00"))
                .packageHeightCm(new BigDecimal("6.00"))
                .build();

        ProductVariantRequest updateRequest = ProductVariantRequest.builder()
                .color("NAVY BLUE")
                .colorHex("#000080")
                .size(Size.XL)
                .stock(25)
                .shippingWeightGrams(new BigDecimal("450.00"))
                .packageLengthCm(new BigDecimal("35.00"))
                .packageWidthCm(new BigDecimal("25.00"))
                .packageHeightCm(new BigDecimal("7.00"))
                .build();

        mapper.updateEntity(entity, updateRequest);

        assertThat(entity.getShippingWeightGrams()).isEqualByComparingTo("450.00");
        assertThat(entity.getPackageLengthCm()).isEqualByComparingTo("35.00");
        assertThat(entity.getPackageWidthCm()).isEqualByComparingTo("25.00");
        assertThat(entity.getPackageHeightCm()).isEqualByComparingTo("7.00");
    }
}
