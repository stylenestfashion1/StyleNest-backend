package com.stylenest.stylenest_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * The seller (StyleNest) side of every invoice -- fixed business/legal
 * data, not per-order. Backed by application.properties (see
 * app.invoice.seller.* below), not the database, since this changes at
 * most a handful of times ever and editing a properties file on deploy is
 * simpler and safer than another admin-editable table for something this
 * sensitive (GSTIN/CIN).
 *
 * Values are the business's own verified data (from the supplied legal
 * policy documents and the reference Vyapar invoice) -- never invented.
 */
@Configuration
@ConfigurationProperties(prefix = "app.invoice.seller")
@Getter
@Setter
public class InvoiceSellerProperties {

    private String name;
    private String addressLine;
    private String phone;
    private String email;
    private String state;
    private String stateCode;
    private String gstin;
    private String cin;
}
