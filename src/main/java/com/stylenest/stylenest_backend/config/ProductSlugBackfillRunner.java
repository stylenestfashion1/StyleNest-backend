package com.stylenest.stylenest_backend.config;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.util.SlugUtil;

import lombok.RequiredArgsConstructor;

/**
 * One-time, idempotent safety net for the product-slug feature. Product.slug
 * is a NOT NULL, unique column already populated for every existing
 * product (verified directly against production before writing this class:
 * zero null/blank/malformed/duplicate slugs found), so this is a no-op on
 * every boot today. It exists for defense against a future edge case this
 * constraint alone can't rule out -- e.g. a product ever inserted by a
 * path that bypasses ProductServiceImpl's own slug assignment.
 *
 * <p>Only ever touches a product whose slug is null/blank. Never
 * regenerates or overwrites an already-set slug (even one that predates
 * this class's improved SlugUtil and might look different if regenerated
 * today) -- that would break an existing bookmarked/shared/indexed URL,
 * exactly the stability guarantee ProductServiceImpl.resolveSlugForUpdate
 * also relies on.
 */
@Component
@RequiredArgsConstructor
public class ProductSlugBackfillRunner implements ApplicationRunner {

    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        List<Product> products = productRepository.findAll();

        for (Product product : products) {

            if (product.getSlug() != null && !product.getSlug().isBlank()) {
                continue;
            }

            String baseSlug = SlugUtil.generateSlug(product.getName());
            String slug = SlugUtil.uniqueSlug(baseSlug, productRepository::existsBySlug);

            product.setSlug(slug);
            productRepository.save(product);
        }
    }
}
