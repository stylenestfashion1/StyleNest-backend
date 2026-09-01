package com.stylenest.stylenest_backend.dto.scrolldown;

import java.time.LocalDateTime;

import com.stylenest.stylenest_backend.enums.Gender;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrollDownImageResponse {

    private Gender gender;

    private Integer step;

    private String imageUrl;

    private LocalDateTime updatedAt;
}
