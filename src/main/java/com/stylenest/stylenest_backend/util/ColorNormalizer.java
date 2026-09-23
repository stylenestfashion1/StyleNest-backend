package com.stylenest.stylenest_backend.util;

/**
 * Color is a free-form admin-entered name, not a fixed enum -- normalizing
 * every write (and every lookup key) to trimmed-uppercase means a casing
 * typo (e.g. "Black" on one variant, "BLACK" on another of the same
 * product) can never silently split what should be one color into two
 * separate groups with their own, out-of-sync image sets.
 */
public final class ColorNormalizer {

    private ColorNormalizer() {
    }

    public static String normalize(String color) {
        return color == null ? null : color.trim().toUpperCase();
    }
}
