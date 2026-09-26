package com.stylenest.stylenest_backend.service;

import java.math.BigDecimal;
import java.util.Optional;

import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.enums.Currency;

// The single place every currency-aware code path (product responses, cart,
// checkout/order creation) goes to find out what a product costs in a given
// currency. INR always resolves straight from Product.price/discountPrice
// (the one authoritative INR source -- see ProductPrice's class comment);
// every other currency resolves from the ProductPrice table and is
// deliberately Optional.empty() -- never a fallback to INR -- when the
// admin hasn't configured that currency for this product yet.
public interface ProductPricingService {

    record ResolvedPrice(BigDecimal regularPrice, BigDecimal discountPrice) {

        // The price actually charged: the sale price when one is set,
        // otherwise the regular price -- the same rule already applied to
        // INR everywhere in the codebase (Cart/Order), now available for
        // any currency.
        public BigDecimal effectivePrice() {
            return discountPrice != null ? discountPrice : regularPrice;
        }
    }

    Optional<ResolvedPrice> resolvePrice(Product product, Currency currency);

    // Creates or overwrites this product's price row for this currency.
    // Used for the INR mirror (called every time Product.price/discountPrice
    // is written, from the same transaction) and for admin-set USD pricing.
    void upsertPrice(Product product, Currency currency, BigDecimal regularPrice, BigDecimal discountPrice);

    // Removes this product's price row for this currency, if one exists.
    // Only ever called for a non-INR currency, on the admin's explicit
    // "clear international pricing" signal -- never for INR, which always
    // has a row as long as the product itself exists.
    void clearPrice(Product product, Currency currency);
}
