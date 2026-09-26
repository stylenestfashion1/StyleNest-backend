package com.stylenest.stylenest_backend.service.impl;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductPrice;
import com.stylenest.stylenest_backend.enums.Currency;
import com.stylenest.stylenest_backend.repository.ProductPriceRepository;
import com.stylenest.stylenest_backend.service.ProductPricingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductPricingServiceImpl implements ProductPricingService {

    private final ProductPriceRepository productPriceRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ResolvedPrice> resolvePrice(Product product, Currency currency) {

        if (currency == Currency.INR) {

            // Authoritative on Product itself -- never "unavailable" the way
            // another currency legitimately can be for a product the admin
            // hasn't priced internationally yet.
            return Optional.of(new ResolvedPrice(product.getPrice(), product.getDiscountPrice()));
        }

        return productPriceRepository.findByProductAndCurrency(product, currency)
                .map(pp -> new ResolvedPrice(pp.getRegularPrice(), pp.getDiscountPrice()));
    }

    @Override
    @Transactional
    public void upsertPrice(Product product, Currency currency, BigDecimal regularPrice, BigDecimal discountPrice) {

        ProductPrice productPrice = productPriceRepository
                .findByProductAndCurrency(product, currency)
                .orElseGet(() -> ProductPrice.builder()
                        .product(product)
                        .currency(currency)
                        .build());

        productPrice.setRegularPrice(regularPrice);
        productPrice.setDiscountPrice(discountPrice);

        productPriceRepository.save(productPrice);
    }

    @Override
    @Transactional
    public void clearPrice(Product product, Currency currency) {

        productPriceRepository.findByProductAndCurrency(product, currency)
                .ifPresent(productPriceRepository::delete);
    }
}
