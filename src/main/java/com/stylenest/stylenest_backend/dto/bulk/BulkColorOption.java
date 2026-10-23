package com.stylenest.stylenest_backend.dto.bulk;

import java.util.ArrayList;
import java.util.List;

import lombok.*;

/**
 * A colorway option for a bulk product.
 * Contains the color name, optional swatch hex, and its dedicated photos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkColorOption {

    private String name; // e.g. "Olive Green", "Jet Black"
    private String hex;  // e.g. "#556B2F"

    @Builder.Default
    private List<String> images = new ArrayList<>();
}
