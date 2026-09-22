package com.example.wallet.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class MockPayment {

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder
                .baseUrl("https://mock-payments") // Set your external API base URL
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
