package com.stylenest.stylenest_backend.dto.bulk;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkAccessValidateResponse {

    private Boolean valid;
}
