-- =============================================================================
-- Migration: Add Product and Variant Shipping Master Data
-- Description: Adds nullable shipping weight (grams) and package dimensions
--              (length, width, height in cm) to product_variants (sellable
--              unit) and products (default baseline).
-- Safety: All columns are strictly NULL. Existing products and variants remain
--         NULL with zero data alteration. No automated DTDC booking enabled.
-- =============================================================================

-- 1. Product Variants (Primary physical shipping dimensions per sellable variant)
ALTER TABLE product_variants
    ADD COLUMN shipping_weight_grams DECIMAL(10, 2) NULL,
    ADD COLUMN package_length_cm DECIMAL(10, 2) NULL,
    ADD COLUMN package_width_cm DECIMAL(10, 2) NULL,
    ADD COLUMN package_height_cm DECIMAL(10, 2) NULL;

-- 2. Products (Product-level default/fallback shipping dimensions)
ALTER TABLE products
    ADD COLUMN shipping_weight_grams DECIMAL(10, 2) NULL,
    ADD COLUMN package_length_cm DECIMAL(10, 2) NULL,
    ADD COLUMN package_width_cm DECIMAL(10, 2) NULL,
    ADD COLUMN package_height_cm DECIMAL(10, 2) NULL;
