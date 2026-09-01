package com.stylenest.stylenest_backend.enums;

/**
 * The customer segment a category (and therefore its products) belongs to.
 * Lives on Category rather than Product so a product's segment is always
 * derived from its category and can never drift out of sync -- adding a
 * new segment (e.g. KIDS) later is just a new enum constant, no schema
 * restructuring.
 */
public enum Gender {
    MEN,
    WOMEN
}
