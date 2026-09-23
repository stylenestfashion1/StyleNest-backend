package com.stylenest.stylenest_backend.dto.product;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReorderImagesRequest {

    @NotEmpty
    private List<Long> orderedImageIds;
}
