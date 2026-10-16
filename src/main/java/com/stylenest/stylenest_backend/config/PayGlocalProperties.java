package com.stylenest.stylenest_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "payglocal")
@Getter
@Setter
public class PayGlocalProperties {

    private String merchantId = "ptplstylene91724";
    private String baseUrl = "https://api.uat.payglocal.in";
    private String privateKeyPath = "src/main/resources/keys/payglocal/merchant_private_key.pem";
    private String privateKeyId = "kId-ubIEen0nWkBVn1d1";
    private String publicKeyPath = "src/main/resources/keys/payglocal/payglocal_public_key.pem";
    private String publicKeyId = "kId-fZS5m8YlJNMr9PgL";
    private String callbackUrl = "https://stylenestfashion.com/api/bulk/payments/payglocal/callback";
}
