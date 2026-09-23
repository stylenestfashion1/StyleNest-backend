package com.stylenest.stylenest_backend.enums;

/**
 * The in-store QR discount is a single offline transaction -- the shopkeeper
 * applies it to the bill immediately once the customer shows the result
 * screen. There is deliberately no ACTIVE/EXPIRED lifecycle: an offer is
 * REDEEMED the moment it's generated.
 */
public enum DiscountOfferStatus {
    REDEEMED
}
