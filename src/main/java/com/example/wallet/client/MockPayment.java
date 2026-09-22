package com.example.wallet.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class MockPayment {

    @Bean
    public RestClient restClient(RestClient.Builder builder, @Value("${wallet.payment.base-url}") String baseUrl) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
