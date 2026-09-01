package com.stylenest.stylenest_backend.util;

public class SlugUtil {

    public static String generateSlug(String text) {

        return text
                .trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-");

    }

}