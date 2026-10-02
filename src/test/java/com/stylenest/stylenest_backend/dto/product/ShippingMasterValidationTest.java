package com.stylenest.stylenest_backend.dto.product;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.stylenest.stylenest_backend.enums.Size;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ShippingMasterValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void productVariantRequest_shouldPassValidation_whenShippingFieldsAreNull() {
        ProductVariantRequest request = ProductVariantRequest.builder()
                .color("BLACK")
                .size(Size.M)
                .stock(10)
                .shippingWeightGrams(null)
                .packageLengthCm(null)
                .packageWidthCm(null)
                .packageHeightCm(null)
                .build();

        Set<ConstraintViolation<ProductVariantRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void productVariantRequest_shouldPassValidation_whenShippingFieldsArePositiveAndWithinBounds() {
        ProductVariantRequest request = ProductVariantRequest.builder()
                .color("BLACK")
                .size(Size.M)
                .stock(10)
                .shippingWeightGrams(new BigDecimal("350.00"))
                .packageLengthCm(new BigDecimal("30.00"))
                .packageWidthCm(new BigDecimal("20.00"))
                .packageHeightCm(new BigDecimal("5.00"))
                .build();

        Set<ConstraintViolation<ProductVariantRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void productVariantRequest_shouldFailValidation_whenWeightIsZeroOrNegative() {
        ProductVariantRequest zeroWeight = ProductVariantRequest.builder()
                .color("BLACK")
                .size(Size.M)
                .stock(10)
                .shippingWeightGrams(BigDecimal.ZERO)
                .build();

        Set<ConstraintViolation<ProductVariantRequest>> zeroViolations = validator.validate(zeroWeight);
        assertThat(zeroViolations).anyMatch(v -> v.getPropertyPath().toString().equals("shippingWeightGrams"));

        ProductVariantRequest negWeight = ProductVariantRequest.builder()
                .color("BLACK")
                .size(Size.M)
                .stock(10)
                .shippingWeightGrams(new BigDecimal("-10.5"))
                .build();

        Set<ConstraintViolation<ProductVariantRequest>> negViolations = validator.validate(negWeight);
        assertThat(negViolations).anyMatch(v -> v.getPropertyPath().toString().equals("shippingWeightGrams"));
    }

    @Test
    void productVariantRequest_shouldFailValidation_whenWeightExceedsUpperLimit() {
        ProductVariantRequest heavy = ProductVariantRequest.builder()
                .color("BLACK")
                .size(Size.M)
                .stock(10)
                .shippingWeightGrams(new BigDecimal("100001.0"))
                .build();

        Set<ConstraintViolation<ProductVariantRequest>> violations = validator.validate(heavy);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("shippingWeightGrams"));
    }

    @Test
    void productVariantRequest_shouldFailValidation_whenDimensionsAreZeroOrNegative() {
        ProductVariantRequest invalidDims = ProductVariantRequest.builder()
                .color("BLACK")
                .size(Size.M)
                .stock(10)
                .packageLengthCm(BigDecimal.ZERO)
                .packageWidthCm(new BigDecimal("-5.0"))
                .packageHeightCm(BigDecimal.ZERO)
                .build();

        Set<ConstraintViolation<ProductVariantRequest>> violations = validator.validate(invalidDims);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("packageLengthCm"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("packageWidthCm"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("packageHeightCm"));
    }

    @Test
    void productVariantRequest_shouldFailValidation_whenDimensionsExceedUpperLimit() {
        ProductVariantRequest huge = ProductVariantRequest.builder()
                .color("BLACK")
                .size(Size.M)
                .stock(10)
                .packageLengthCm(new BigDecimal("501.0"))
                .packageWidthCm(new BigDecimal("600.0"))
                .packageHeightCm(new BigDecimal("550.0"))
                .build();

        Set<ConstraintViolation<ProductVariantRequest>> violations = validator.validate(huge);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("packageLengthCm"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("packageWidthCm"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("packageHeightCm"));
    }

    @Test
    void productRequest_shouldPassValidation_whenShippingFieldsAreNull() {
        ProductRequest request = ProductRequest.builder()
                .name("Silk Kurti")
                .price(new BigDecimal("1299.00"))
                .categoryId(1L)
                .build();

        Set<ConstraintViolation<ProductRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void productRequest_shouldFailValidation_whenShippingFieldsAreZeroOrNegative() {
        ProductRequest request = ProductRequest.builder()
                .name("Silk Kurti")
                .price(new BigDecimal("1299.00"))
                .categoryId(1L)
                .shippingWeightGrams(BigDecimal.ZERO)
                .packageLengthCm(new BigDecimal("-1.0"))
                .build();

        Set<ConstraintViolation<ProductRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("shippingWeightGrams"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("packageLengthCm"));
    }
}
