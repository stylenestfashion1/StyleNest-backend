package com.stylenest.stylenest_backend.config;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.enums.Currency;
import com.stylenest.stylenest_backend.repository.OrderRepository;
import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.service.ProductPricingService;

import lombok.RequiredArgsConstructor;

/**
 * One-time, idempotent data backfill for the INR/USD pricing feature. Runs
 * on every boot; only ever touches rows that are still missing the new
 * data, never overwrites anything already set.
 *
 * <p>1. Every existing Product gets an INR ProductPrice row mirroring its
 * existing price/discountPrice columns -- exactly what's already there,
 * never invented. USD is never touched here; it stays absent until an
 * admin explicitly configures it.
 *
 * <p>2. Every existing Order with no currency set gets backfilled to INR.
 * This is safe because every order ever placed on this system was
 * verified (queried directly against production before writing this
 * class) to have shipped to India -- there is no other possibility for
 * historical rows, since the currency concept didn't exist before this
 * feature and Easebuzz (the only payment gateway ever used) is INR-only.
 */
@Component
@RequiredArgsConstructor
public class PricingBackfillRunner implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final ProductPricingService productPricingService;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        backfillInrProductPrices();
        backfillOrderCurrency();
    }

    private void backfillInrProductPrices() {

        List<Product> products = productRepository.findAll();

        for (Product product : products) {

            // upsertPrice() is safe to call unconditionally (create-or-
            // update), but skipping products that already have their INR
            // mirror row avoids a pointless write on every single boot
            // once the one-time backfill has actually happened.
            if (hasInrRow(product)) {
                continue;
            }

            productPricingService.upsertPrice(
                    product, Currency.INR, product.getPrice(), product.getDiscountPrice());
        }
    }

    private boolean hasInrRow(Product product) {
        return product.getPrices().stream()
                .anyMatch(pp -> pp.getCurrency() == Currency.INR);
    }

    private void backfillOrderCurrency() {

        List<Order> unbackfilled = orderRepository.findByCurrencyIsNull();

        for (Order order : unbackfilled) {
            order.setCurrency(Currency.INR);
        }

        orderRepository.saveAll(unbackfilled);
    }
}
