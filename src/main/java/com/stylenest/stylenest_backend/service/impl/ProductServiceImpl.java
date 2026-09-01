package com.stylenest.stylenest_backend.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.product.ProductRequest;
import com.stylenest.stylenest_backend.dto.product.ProductResponse;
import com.stylenest.stylenest_backend.dto.product.filter.ProductFilterRequest;
import com.stylenest.stylenest_backend.entity.Category;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.exception.DuplicateResourceException;
import com.stylenest.stylenest_backend.exception.ProductHasOrderHistoryException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.ProductMapper;
import com.stylenest.stylenest_backend.repository.CartItemRepository;
import com.stylenest.stylenest_backend.repository.CategoryRepository;
import com.stylenest.stylenest_backend.repository.OrderItemRepository;
import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.repository.WishlistItemRepository;
import com.stylenest.stylenest_backend.service.ProductSearchMeta;
import com.stylenest.stylenest_backend.service.ProductService;
import com.stylenest.stylenest_backend.service.ProductThumbnailResolver;
import com.stylenest.stylenest_backend.specification.ProductSpecification;
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

        product.setSlug(slug);
        product.setCategory(category);

        Product savedProduct = productRepository.save(product);

        return productMapper.toResponse(savedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {

        List<Product> products = productRepository.findAll();

        Map<Long, ProductSearchMeta> meta = thumbnailResolver.resolveMeta(
                products.stream().map(Product::getId).toList(), null, null);

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

        ProductSearchMeta meta = thumbnailResolver.resolveMeta(List.of(id), null, null)
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

        // Cascades to product_variants and, via each variant, to
        // product_images (both cascade=ALL, orphanRemoval=true on the
        // entity mappings).
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
                request.getColor(),
                request.getSize());

        return products.map(product -> productMapper.toResponse(
                product, meta.getOrDefault(product.getId(), ProductSearchMeta.EMPTY)));
    }
}