package com.stylenest.stylenest_backend.dto.scrolldown;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrollDownImageRequest {

    @NotBlank(message = "imageUrl is required")
    private String imageUrl;
}
