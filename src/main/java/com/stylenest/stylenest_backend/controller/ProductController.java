package com.stylenest.stylenest_backend.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.product.ProductRequest;
import com.stylenest.stylenest_backend.dto.product.ProductResponse;
import com.stylenest.stylenest_backend.dto.product.filter.ProductFilterRequest;
import com.stylenest.stylenest_backend.enums.Gender;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.ProductService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody ProductRequest request) {

        ProductResponse response = productService.createProduct(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<ProductResponse>builder()
                        .success(true)
                        .message("Product created successfully.")
                        .data(response)
                        .build());
    }

    // gender/categoryId are optional so this endpoint's old no-arg contract
    // (return everything) is unchanged for existing callers -- but previously
    // ANY query param passed here (e.g. ?category=Kurti&gender=WOMEN) was
    // silently ignored because nothing was bound to it, so a caller filtering
    // by category would actually get every product back, unfiltered. That
    // never showed up on the storefront because the frontend calls
    // /products/filter (see filterProducts below) instead of this endpoint,
    // but any other caller hitting this one expecting the query to filter
    // would get the wrong result. Delegates to the same
    // ProductSpecification-backed search as /filter so both endpoints agree.
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts(
            @RequestParam(required = false) Gender gender,
            @RequestParam(required = false) Long categoryId) {

        List<ProductResponse> response;
        if (gender != null || categoryId != null) {
            ProductFilterRequest request = ProductFilterRequest.builder()
                    .gender(gender)
                    .categoryId(categoryId)
                    .sizePerPage(Integer.MAX_VALUE)
                    .build();
            response = productService.searchProducts(request).getContent();
        } else {
            response = productService.getAllProducts();
        }

        return ResponseEntity.ok(
                ApiResponse.<List<ProductResponse>>builder()
                        .success(true)
                        .message("Products fetched successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(
            @PathVariable Long id) {

        ProductResponse response = productService.getProductById(id);

        return ResponseEntity.ok(
                ApiResponse.<ProductResponse>builder()
                        .success(true)
                        .message("Product fetched successfully.")
                        .data(response)
                        .build());
    }

    // The canonical public product-detail lookup -- the frontend's
    // /products/:slugOrId route resolves through this for anything that
    // isn't a purely-numeric legacy ID (see SlugUtil.generateSlug, which
    // guarantees a generated slug is never purely numeric, so there's no
    // ambiguity with the {id} route above).
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductBySlug(
            @PathVariable String slug) {

        ProductResponse response = productService.getProductBySlug(slug);

        return ResponseEntity.ok(
                ApiResponse.<ProductResponse>builder()
                        .success(true)
                        .message("Product fetched successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {

        ProductResponse response = productService.updateProduct(id, request);

        return ResponseEntity.ok(
                ApiResponse.<ProductResponse>builder()
                        .success(true)
                        .message("Product updated successfully.")
                        .data(response)
                        .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable Long id) {

        productService.deleteProduct(id);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Product deleted successfully.")
                        .data(null)
                        .build());
    }
    
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> searchProducts(
            @RequestBody ProductFilterRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Products fetched successfully",
                        productService.searchProducts(request)
                )
        );
    }

    // Query-param equivalent of POST /search, e.g.
    // GET /api/products/filter?gender=MEN&categoryId=3&minPrice=500&maxPrice=2000&search=shirt
    // Kept separate from the plain GET /api/products above so that
    // endpoint's existing unfiltered/unpaginated contract never changes.
    @GetMapping("/filter")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> filterProducts(
            @RequestParam(required = false) Gender gender,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) Boolean trending,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "12") Integer sizePerPage,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        ProductFilterRequest request = ProductFilterRequest.builder()
                .gender(gender)
                .categoryId(categoryId)
                .color(color)
                .size(size)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .keyword(search)
                .featured(featured)
                .trending(trending)
                .active(active)
                .page(page)
                .sizePerPage(sizePerPage)
                .sortBy(sortBy)
                .direction(direction)
                .build();

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Products fetched successfully",
                        productService.searchProducts(request)
                )
        );
    }
}