package com.stylenest.stylenest_backend.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.product.ProductJeansCodeResponse;
import com.stylenest.stylenest_backend.dto.product.ProductRequest;
import com.stylenest.stylenest_backend.dto.product.ProductResponse;
import com.stylenest.stylenest_backend.dto.product.filter.ProductFilterRequest;
import com.stylenest.stylenest_backend.entity.Category;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.exception.DuplicateResourceException;
import com.stylenest.stylenest_backend.exception.ProductHasOrderHistoryException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.ProductMapper;
import com.stylenest.stylenest_backend.entity.ProductImage;
import com.stylenest.stylenest_backend.repository.CartItemRepository;
import com.stylenest.stylenest_backend.repository.CategoryRepository;
import com.stylenest.stylenest_backend.repository.OrderItemRepository;
import com.stylenest.stylenest_backend.repository.ProductImageRepository;
import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.repository.WishlistItemRepository;
import com.stylenest.stylenest_backend.service.ImageStorageService;
import com.stylenest.stylenest_backend.service.ProductSearchMeta;
import com.stylenest.stylenest_backend.service.ProductService;
import com.stylenest.stylenest_backend.service.ProductThumbnailResolver;
import com.stylenest.stylenest_backend.specification.ProductSpecification;
import com.stylenest.stylenest_backend.util.SkuGenerator;
import com.stylenest.stylenest_backend.util.SlugUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final ProductThumbnailResolver thumbnailResolver;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductImageRepository productImageRepository;
    private final ImageStorageService imageStorageService;

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {

        String slug = SlugUtil.generateSlug(request.getName());

        if (productRepository.existsBySlug(slug)) {
            throw new DuplicateResourceException(
                    "Product with this name already exists.");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Category not found with id: " + request.getCategoryId()));

        Product product = productMapper.toEntity(request);

        assertJeansCodeAvailable(product.getJeansCode(), null);

        product.setSlug(slug);
        product.setCategory(category);

        // Deterministic short product code (e.g. "UGT") used as the prefix
        // for every variant SKU -- see SkuGenerator.
        Set<String> existingPrefixes = new HashSet<>(productRepository.findAllSkus());
        product.setSku(SkuGenerator.productPrefix(request.getName(), existingPrefixes));

        Product savedProduct = productRepository.save(product);

        return productMapper.toResponse(savedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {

        List<Product> products = productRepository.findAll();

        Map<Long, ProductSearchMeta> meta = thumbnailResolver.resolveMeta(
                products.stream().map(Product::getId).toList(), null);

        return products.stream()
                .map(product -> productMapper.toResponse(
                        product, meta.getOrDefault(product.getId(), ProductSearchMeta.EMPTY)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with id: " + id));

        ProductSearchMeta meta = thumbnailResolver.resolveMeta(List.of(id), null)
                .getOrDefault(id, ProductSearchMeta.EMPTY);

        return productMapper.toResponse(product, meta);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with id: " + id));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Category not found with id: " + request.getCategoryId()));

        String slug = SlugUtil.generateSlug(request.getName());

        if (!product.getSlug().equals(slug)
                && productRepository.existsBySlug(slug)) {

            throw new DuplicateResourceException(
                    "Product with this name already exists.");
        }

        productMapper.updateEntity(product, request);

        assertJeansCodeAvailable(product.getJeansCode(), id);

        product.setCategory(category);
        product.setSlug(slug);

        Product updatedProduct = productRepository.save(product);

        return productMapper.toResponse(updatedProduct);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with id: " + id));

        // Orders are historical business records -- a product that has
        // ever been ordered must not be hard-deleted (it would either
        // fail on the order_items FK or, worse, silently corrupt order
        // history). Admins should deactivate it instead.
        if (orderItemRepository.existsByProductVariant_Product_Id(id)) {
            throw new ProductHasOrderHistoryException(
                    "Cannot delete a product with existing orders. "
                            + "Deactivate it instead via PUT /api/admin/products/"
                            + id + " with active:false.");
        }

        // Cart and wishlist entries are ephemeral/mutable, not historical
        // records -- safe to remove as part of deleting the product.
        // Must happen before deleting the product/variants: cart_items
        // and wishlist_items are not JPA children of Product/ProductVariant,
        // so Hibernate won't cascade-remove them, and the FK (NO ACTION)
        // would otherwise block the variant/product deletes below.
        cartItemRepository.deleteByProductVariant_Product_Id(id);
        wishlistItemRepository.deleteByProductId(id);

        // Images are keyed by (product, color) now, not owned by
        // ProductVariant, so they are no longer reached by the
        // product_variants cascade below -- delete them explicitly first
        // (and clean up their backing files) or they'd be orphaned rows
        // pointing at a product_id that no longer exists.
        List<ProductImage> images = productImageRepository.findByProductIdOrderByColorAscDisplayOrderAsc(id);
        productImageRepository.deleteAll(images);
        images.forEach(image -> imageStorageService.deleteIfManaged(image.getImageUrl()));

        // Cascades to product_variants (cascade=ALL, orphanRemoval=true on
        // the entity mapping).
        productRepository.delete(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(ProductFilterRequest request) {

        Sort sort = Sort.by(
                Sort.Direction.fromString(request.getDirection()),
                request.getSortBy());

        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getSizePerPage(),
                sort);

        Page<Product> products = productRepository.findAll(
                ProductSpecification.filterProducts(request),
                pageable);

        Map<Long, ProductSearchMeta> meta = thumbnailResolver.resolveMeta(
                products.getContent().stream().map(Product::getId).toList(),
                request.getColor());

        return products.map(product -> productMapper.toResponse(
                product, meta.getOrDefault(product.getId(), ProductSearchMeta.EMPTY)));
    }

    // A jeans code is meant to uniquely identify one product (per the
    // client's stated use case), so it's rejected as a duplicate the same
    // way a duplicate name/slug already is above -- but only when a code
    // is actually present; most products have none (null), and MySQL's
    // UNIQUE constraint on jeans_code already permits any number of
    // NULLs, so those never collide with each other.
    private void assertJeansCodeAvailable(String jeansCode, Long excludeProductId) {

        if (jeansCode == null) {
            return;
        }

        boolean taken = excludeProductId == null
                ? productRepository.existsByJeansCodeIgnoreCase(jeansCode)
                : productRepository.existsByJeansCodeIgnoreCaseAndIdNot(jeansCode, excludeProductId);

        if (taken) {
            throw new DuplicateResourceException(
                    "Jeans code \"" + jeansCode + "\" is already used by another product.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProductJeansCodeResponse getJeansCode(Long id) {

        return productRepository.findJeansCodeByProductId(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductJeansCodeResponse> getAllJeansCodes() {
        return productRepository.findAllJeansCodes();
    }
}