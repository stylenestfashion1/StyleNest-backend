package com.stylenest.stylenest_backend.mapper;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.scrolldown.ScrollDownImageResponse;
import com.stylenest.stylenest_backend.entity.ScrollDownImage;

@Component
public class ScrollDownImageMapper {

    public ScrollDownImageResponse toResponse(ScrollDownImage image) {

        return ScrollDownImageResponse.builder()
                .gender(image.getGender())
                .step(image.getStep())
                .imageUrl(image.getImageUrl())
                .updatedAt(image.getUpdatedAt())
                .build();
    }
}
