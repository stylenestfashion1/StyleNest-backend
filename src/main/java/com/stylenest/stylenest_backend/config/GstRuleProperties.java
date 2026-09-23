package com.stylenest.stylenest_backend.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * The CENTRALIZED, single source of truth for StyleNest's apparel GST slab
 * rule (CBIC rate rationalization, HSN Chapters 61/62, effective
 * 22-Sep-2025): a per-piece sale value at or below {@link #apparelThreshold}
 * uses {@link #rateAtOrBelowThreshold}; above it uses
 * {@link #rateAboveThreshold}. See GstCalculationServiceImpl for where
 * these are actually applied.
 *
 * If the GST Council revises the applicable apparel threshold or rates in
 * the future, these three values (application.properties, below) are the
 * ONLY thing that needs to change -- every new order/invoice across both
 * retail and bulk automatically picks up the new rule, with zero other
 * code changes and zero per-product edits. Already-finalized invoices are
 * never affected: each one permanently snapshots the rate that was
 * actually applied at the moment it was generated (see
 * Invoice/InvoiceItem), and is never recomputed afterward.
 *
 * Deliberately NOT auto-fetched from any government API/internet source --
 * a GST rate/threshold change is a deliberate legal event, not something to
 * silently automate. An authorized developer updates these values only
 * once the business has confirmed a new applicable rate.
 */
@Configuration
@ConfigurationProperties(prefix = "app.gst")
@Getter
@Setter
public class GstRuleProperties {

    private BigDecimal apparelThreshold = new BigDecimal("2500.00");
    private BigDecimal rateAtOrBelowThreshold = new BigDecimal("5");
    private BigDecimal rateAboveThreshold = new BigDecimal("18");
}
