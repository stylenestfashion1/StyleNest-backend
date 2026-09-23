package com.stylenest.stylenest_backend.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Generates deterministic, human-readable SKU components, matching the algorithm used for the
 * one-time catalog backfill (see the 2026-09 SKU cleanup): a 3-letter product prefix derived from
 * the product name, and a 3-letter color code derived from the variant's color. The full variant
 * SKU is assembled as {@code STN-<productPrefix>-<colorCode>-<sizeLabel>} by the caller.
 *
 * <p>Both halves are collision-resistant against a caller-supplied set of codes already in use
 * (existing product prefixes, or existing color codes on the same product) so two different
 * products/colors never end up sharing a code.
 */
public final class SkuGenerator {

    private static final Set<String> STOPWORDS = Set.of("with", "and", "the", "for", "a", "an", "of", "in", "to");

    private SkuGenerator() {
    }

    /**
     * Derives a 3-letter product prefix from the product name (e.g. "Urban Graphic Tee" -&gt;
     * "UGT", "Star-Embroidered Layered Shirt" -&gt; "SEL"): the initials of the first three
     * significant words (stopwords filtered, hyphens treated as word breaks), padded from the
     * last significant word if there aren't three. Collision-resistant against existingPrefixes.
     */
    public static String productPrefix(String productName, Set<String> existingPrefixes) {
        List<String> significantWords = significantWords(productName);
        String base = initials(significantWords, 3);
        return uniquify(base, existingPrefixes);
    }

    /**
     * Derives a 3-letter color code for a variant SKU (e.g. "BLACK" -&gt; "BLA", "DARK BLUE"
     * -&gt; "DBL"): the first 3 letters for a single-word color, or the initials of each word
     * (padded from the last word) for a multi-word color. Collision-resistant against
     * usedCodesForProduct so two different colors on the same product never collide.
     */
    public static String colorCode(String color, Set<String> usedCodesForProduct) {
        List<String> words = significantWords(color);
        if (words.isEmpty()) {
            words = List.of("COL");
        }
        String base = words.size() == 1 ? pad(words.get(0), 3) : initials(words, 3);
        return uniquify(base, usedCodesForProduct);
    }

    private static List<String> significantWords(String text) {
        List<String> words = new ArrayList<>();
        for (String raw : text.trim().split("[\\s-]+")) {
            String clean = raw.replaceAll("[^A-Za-z]", "");
            if (clean.isEmpty() || STOPWORDS.contains(clean.toLowerCase())) {
                continue;
            }
            words.add(clean.toUpperCase());
        }
        return words;
    }

    /** First letter of up to {@code length} words, padded from the last word's remaining letters. */
    private static String initials(List<String> words, int length) {
        if (words.isEmpty()) {
            return "X".repeat(length);
        }
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() == length) {
                break;
            }
            result.append(word.charAt(0));
        }
        if (result.length() < length) {
            String last = words.get(words.size() - 1);
            for (int i = 1; i < last.length() && result.length() < length; i++) {
                result.append(last.charAt(i));
            }
        }
        return pad(result.toString(), length);
    }

    /** Truncates to {@code length}, or pads by repeating the last character (then 'X'). */
    private static String pad(String s, int length) {
        if (s.length() >= length) {
            return s.substring(0, length);
        }
        StringBuilder result = new StringBuilder(s);
        char filler = s.isEmpty() ? 'X' : s.charAt(s.length() - 1);
        while (result.length() < length) {
            result.append(filler);
        }
        return result.toString();
    }

    private static String uniquify(String base, Set<String> existing) {
        if (!existing.contains(base)) {
            return base;
        }
        for (int i = 2; i < 1000; i++) {
            String suffix = String.valueOf(i);
            String candidate = base.substring(0, Math.max(1, base.length() - suffix.length())) + suffix;
            if (!existing.contains(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique code from base: " + base);
    }
}
