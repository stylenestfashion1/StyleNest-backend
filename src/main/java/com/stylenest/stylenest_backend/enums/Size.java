package com.stylenest.stylenest_backend.enums;

public enum Size {

    // Apparel sizes -- used by every category except Jeans.
    XS("XS", "XS"),
    S("S", "S"),
    M("M", "M"),
    L("L", "L"),
    XL("XL", "XL"),
    XXL("XXL", "XXL"),

    // Extended apparel sizes -- available system-wide (one shared enum, same
    // as everything else here) but the frontend's sizesForCategory only
    // offers these for Palazzo (see src/utils/sizeLabel.js); an admin
    // decides per-product which of these, if any, that product actually
    // needs. Never auto-created for every Palazzo -- see the 2026-09 Free
    // Size rollout, which only ever inserts FREE_SIZE automatically.
    SIZE_3XL("3XL", "3XL"),
    SIZE_4XL("4XL", "4XL"),
    SIZE_5XL("5XL", "5XL"),
    SIZE_6XL("6XL", "6XL"),
    SIZE_7XL("7XL", "7XL"),

    // "Free Size" contains a space, which is fine for the customer/admin
    // facing label but not for a SKU segment (see skuToken) -- kept as its
    // own value, deliberately not folded into the Jeans/apparel lists.
    FREE_SIZE("Free Size", "FS"),

    // Jeans waist sizes (inches) -- used ONLY by the Jeans category. Named
    // with a SIZE_ prefix since a bare "28" isn't a legal Java identifier;
    // getLabel() (and the frontend's matching src/utils/sizeLabel.js) is
    // what displays the clean "28"/"30"/... text instead of the raw enum
    // name -- this is the one backend spot to use for any one-way,
    // backend-rendered text (invoice PDF, confirmation email) where there
    // is no frontend remapping layer downstream. API fields that round-trip
    // back into a request (cart/wishlist/order size params) must keep using
    // the raw enum name(), never the label.
    SIZE_26("26", "26"),
    SIZE_28("28", "28"),
    SIZE_30("30", "30"),
    SIZE_32("32", "32"),
    SIZE_34("34", "34"),
    SIZE_36("36", "36"),
    SIZE_38("38", "38"),
    SIZE_40("40", "40");

    private final String label;
    // The token used when building a variant SKU (see SkuGenerator /
    // ProductVariantServiceImpl.generateVariantSku) -- identical to the
    // label for every size except FREE_SIZE, whose label contains a space
    // that would otherwise land inside a "-"-delimited SKU.
    private final String skuToken;

    Size(String label, String skuToken) {
        this.label = label;
        this.skuToken = skuToken;
    }

    public String getLabel() {
        return label;
    }

    public String getSkuToken() {
        return skuToken;
    }

}
