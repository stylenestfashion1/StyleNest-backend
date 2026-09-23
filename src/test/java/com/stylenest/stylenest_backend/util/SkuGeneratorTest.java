package com.stylenest.stylenest_backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class SkuGeneratorTest {

    @Test
    void productPrefix_takesInitialsOfFirstThreeSignificantWords() {
        assertThat(SkuGenerator.productPrefix("Urban Graphic Tee", Set.of())).isEqualTo("UGT");
        assertThat(SkuGenerator.productPrefix("Textured Knit Co-ord Set", Set.of())).isEqualTo("TKC");
        assertThat(SkuGenerator.productPrefix("Tapered Fit Denim", Set.of())).isEqualTo("TFD");
    }

    @Test
    void productPrefix_treatsHyphensAsWordBreaks() {
        // "Star-Embroidered" splits into "Star" + "Embroidered" -- matches the
        // backfilled catalog value ("SEL"), not "SLS" (treating it as one word).
        assertThat(SkuGenerator.productPrefix("Star-Embroidered Layered Shirt", Set.of())).isEqualTo("SEL");
    }

    @Test
    void productPrefix_filtersStopwords() {
        // "with"/"the" are filtered, leaving only "Shirt" + "Collar" (2
        // significant words) -- padded from "Collar"[1] = 'O' -> "SCO".
        assertThat(SkuGenerator.productPrefix("Shirt with the Collar", Set.of())).isEqualTo("SCO");
    }

    @Test
    void productPrefix_padsFromLastWordWhenFewerThanThreeSignificantWords() {
        // Only one significant word ("Tee") -- pad using its own remaining letters.
        assertThat(SkuGenerator.productPrefix("Tee", Set.of())).isEqualTo("TEE");
    }

    @Test
    void productPrefix_isCollisionResistant() {
        String first = SkuGenerator.productPrefix("Urban Graphic Tee", Set.of());
        String second = SkuGenerator.productPrefix("Urban Graphic Tee", Set.of(first));

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void colorCode_takesFirstThreeLettersForSingleWordColor() {
        assertThat(SkuGenerator.colorCode("BLACK", Set.of())).isEqualTo("BLA");
        assertThat(SkuGenerator.colorCode("BLUE", Set.of())).isEqualTo("BLU");
        assertThat(SkuGenerator.colorCode("LAVENDER", Set.of())).isEqualTo("LAV");
    }

    @Test
    void colorCode_padsMultiWordColorFromLastWord() {
        // "DARK" + "BLUE" -> initials "D","B" (2 letters), padded with the
        // next letter of the last word ("BLUE"[1] = 'L') -> "DBL".
        assertThat(SkuGenerator.colorCode("DARK BLUE", Set.of())).isEqualTo("DBL");
    }

    @Test
    void colorCode_isCollisionResistantWithinSameProduct() {
        String blue = SkuGenerator.colorCode("BLUE", Set.of());
        String black = SkuGenerator.colorCode("BLACK", Set.of(blue));

        assertThat(black).isNotEqualTo(blue);
    }

    @Test
    void colorCode_shortColorNameIsPaddedNotLeftShort() {
        // "RED" is already 3 letters -- no padding needed.
        assertThat(SkuGenerator.colorCode("RED", Set.of())).isEqualTo("RED");
    }
}
