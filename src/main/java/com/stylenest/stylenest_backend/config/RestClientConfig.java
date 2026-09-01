package com.stylenest.stylenest_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    @Primary
    public RestClient restClient() {
        return RestClient.create();
    }

    // Dedicated, short-timeout client for the postal-lookup provider: this
    // call must never be able to hang/block checkout (it's a
    // convenience/auto-suggest feature, not a required step).
    @Bean
    public RestClient postalLookupRestClient(
            @Value("${postal-lookup.timeout-ms:3000}") int timeoutMs) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

}