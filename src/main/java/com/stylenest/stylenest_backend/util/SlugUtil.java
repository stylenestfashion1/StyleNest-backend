package com.stylenest.stylenest_backend.util;

import java.text.Normalizer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Generates clean, URL-safe product slugs and makes them unique against a
 * collision check the caller supplies. Stability (never re-deriving an
 * already-assigned slug just because the source text changed later) is the
 * caller's responsibility -- see ProductServiceImpl -- this class only
 * turns text into a slug and, separately, makes a candidate unique.
 */
public class SlugUtil {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern INVALID_CHARS = Pattern.compile("[^a-z0-9\\s-]");
    // Collapses a run of whitespace AND/OR hyphens (in any mix, e.g. " - ")
    // into a single hyphen -- this is what actually prevents "cordset - set"
    // from becoming "cordset---set"; collapsing whitespace alone is not
    // enough once literal hyphens already in the source text are involved.
    private static final Pattern WHITESPACE_OR_HYPHENS = Pattern.compile("[\\s-]+");
    private static final Pattern LEADING_TRAILING_HYPHENS = Pattern.compile("^-+|-+$");
    private static final Pattern PURELY_NUMERIC = Pattern.compile("\\d+");

    private SlugUtil() {
    }

    /**
     * A slug that came out purely numeric (e.g. a product literally named
     * "2026") would be indistinguishable from a legacy numeric product ID
     * in the public /products/{slugOrId} route, so one is never produced --
     * see the frontend's numeric-vs-slug routing check, which relies on
     * this guarantee.
     */
    public static String generateSlug(String text) {

        if (text == null) {
            return fallback();
        }

        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(decomposed).replaceAll(""); // "café" -> "cafe", not "caf"

        String slug = withoutDiacritics.trim().toLowerCase();
        slug = INVALID_CHARS.matcher(slug).replaceAll("");
        slug = WHITESPACE_OR_HYPHENS.matcher(slug).replaceAll("-");
        slug = LEADING_TRAILING_HYPHENS.matcher(slug).replaceAll("");

        if (slug.isEmpty()) {
            return fallback();
        }

        if (PURELY_NUMERIC.matcher(slug).matches()) {
            return slug + "-p";
        }

        return slug;
    }

    private static String fallback() {
        return "product";
    }

    /**
     * Appends -2, -3, ... to baseSlug until existsExcludingSelf reports no
     * collision. Deterministic: always starts at -2 and stops at the first
     * free candidate, so calling this again with the same inputs and the
     * same existing data produces the same result -- it is only ever
     * invoked once, at the moment a slug is first assigned (see
     * ProductServiceImpl), never re-run against a slug that's already
     * saved, so re-running a backfill twice cannot chain into -3, -4, etc.
     */
    public static String uniqueSlug(String baseSlug, Predicate<String> existsExcludingSelf) {

        if (!existsExcludingSelf.test(baseSlug)) {
            return baseSlug;
        }

        int suffix = 2;
        String candidate;

        do {
            candidate = baseSlug + "-" + suffix;
            suffix++;
        } while (existsExcludingSelf.test(candidate));

        return candidate;
    }
}
