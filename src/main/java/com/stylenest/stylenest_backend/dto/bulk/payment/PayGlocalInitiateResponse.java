package com.stylenest.stylenest_backend.dto.bulk.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayGlocalInitiateResponse {

    private String bulkOrderNumber;
    private String redirectUrl;
    private String gid;
    private String status;
}
