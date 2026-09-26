package com.stylenest.stylenest_backend.enums;

// Every currency a product can independently be priced in. INR is the
// original, always-present market (mirrored from Product.price/discountPrice
// -- see ProductPrice). Adding a new market later (EUR, GBP, AED, ...) is
// just a new constant here plus an admin UI section -- this is mapped via
// @Enumerated(EnumType.STRING) on a plain Hibernate-generated VARCHAR column
// (see ProductPrice), never a native MySQL ENUM column, so it never needs a
// manual ALTER TABLE the way the legacy Size column did.
public enum Currency {
    INR,
    USD
}
