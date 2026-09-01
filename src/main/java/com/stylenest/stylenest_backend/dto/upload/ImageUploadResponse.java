package com.stylenest.stylenest_backend.dto.upload;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageUploadResponse {

    private String url;

    private String filename;
}
