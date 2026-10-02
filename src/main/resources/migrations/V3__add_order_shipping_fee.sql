-- =============================================================================
-- Migration: Add Order Shipping Fee
-- Description: Adds shipping_fee to orders table to store the dynamic DTDC
--              shipping charge assessed at checkout time.
-- Safety: Column is NULL with DEFAULT 0.00. Existing orders default to 0.00.
-- =============================================================================

ALTER TABLE orders
    ADD COLUMN shipping_fee DECIMAL(10, 2) NULL DEFAULT 0.00;
