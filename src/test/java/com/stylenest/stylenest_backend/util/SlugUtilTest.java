package com.stylenest.stylenest_backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugUtilTest {

    @Test
    void generateSlug_simpleName_lowercasesAndHyphenates() {
        assertThat(SlugUtil.generateSlug("Pink Cotton Kurti")).isEqualTo("pink-cotton-kurti");
    }

    @Test
    void generateSlug_apostrophe_isStrippedNotHyphenated() {
        assertThat(SlugUtil.generateSlug("Women's Printed Kurti")).isEqualTo("womens-printed-kurti");
    }

    @Test
    void generateSlug_multipleSpaces_collapseToSingleHyphen() {
        assertThat(SlugUtil.generateSlug("Blue   Floral   Cotton Kurti")).isEqualTo("blue-floral-cotton-kurti");
    }

    @Test
    void generateSlug_ampersand_isStrippedWithoutLeavingDoubleHyphen() {
        assertThat(SlugUtil.generateSlug("Pink & Cotton Kurti")).isEqualTo("pink-cotton-kurti");
    }

    @Test
    void generateSlug_hyphenSurroundedBySpaces_doesNotProduceRepeatedHyphens() {
        // The exact bug this class fixes: naive whitespace-only collapsing
        // turned "cordset - set 1" into "cordset---set-1".
        assertThat(SlugUtil.generateSlug("Cordset - Set 1")).isEqualTo("cordset-set-1");
    }

    @Test
    void generateSlug_leadingAndTrailingHyphens_areTrimmed() {
        assertThat(SlugUtil.generateSlug("-Pink Kurti-")).isEqualTo("pink-kurti");
    }

    @Test
    void generateSlug_accentedCharacters_transliteratedNotDropped() {
        assertThat(SlugUtil.generateSlug("Café Cotton Kurti")).isEqualTo("cafe-cotton-kurti");
    }

    @Test
    void generateSlug_purelyNumericName_getsNonNumericSuffix() {
        // A slug that came out purely numeric would be indistinguishable
        // from a legacy numeric product ID in the /products/{slugOrId}
        // route -- never allowed to happen.
        assertThat(SlugUtil.generateSlug("2026")).isEqualTo("2026-p");
    }

    @Test
    void generateSlug_allSymbols_fallsBackToProductRatherThanEmpty() {
        assertThat(SlugUtil.generateSlug("!!!")).isEqualTo("product");
    }

    @Test
    void generateSlug_null_fallsBackToProduct() {
        assertThat(SlugUtil.generateSlug(null)).isEqualTo("product");
    }

    @Test
    void generateSlug_repeatedCallsOnAlreadyCleanSlug_isIdempotent() {
        String once = SlugUtil.generateSlug("Blue Floral Cotton Kurti");
        String twice = SlugUtil.generateSlug(once);
        assertThat(twice).isEqualTo(once);
    }

    @Test
    void uniqueSlug_noCollision_returnsBaseUnchanged() {
        assertThat(SlugUtil.uniqueSlug("pink-cotton-kurti", s -> false)).isEqualTo("pink-cotton-kurti");
    }

    @Test
    void uniqueSlug_oneCollision_appendsDashTwo() {
        assertThat(SlugUtil.uniqueSlug("pink-cotton-kurti", s -> s.equals("pink-cotton-kurti")))
                .isEqualTo("pink-cotton-kurti-2");
    }

    @Test
    void uniqueSlug_multipleCollisions_incrementsUntilFree() {
        assertThat(SlugUtil.uniqueSlug("pink-cotton-kurti",
                s -> s.equals("pink-cotton-kurti") || s.equals("pink-cotton-kurti-2") || s.equals("pink-cotton-kurti-3")))
                .isEqualTo("pink-cotton-kurti-4");
    }
}
