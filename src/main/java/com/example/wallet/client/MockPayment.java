package com.example.wallet.client;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class MockPayment {

    /**
     * Connect/read timeout is what makes GatewaySimulator.Mode.TIMEOUT observable client-side --
     * without a bounded timeout, RestClient would just wait forever for a delayed gateway response
     * instead of throwing, and the retry logic in PaymentProcessor would have nothing to react to.
     */
    @Bean
    public RestClient restClient(
            RestClient.Builder builder,
            @Value("${wallet.payment.base-url}") String baseUrl,
            @Value("${wallet.payment.timeout-ms}") long timeoutMs) {
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(timeoutMs))
                .withReadTimeout(Duration.ofMillis(timeoutMs));
        var requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .requestFactory(requestFactory)
                .build();
    }
}
