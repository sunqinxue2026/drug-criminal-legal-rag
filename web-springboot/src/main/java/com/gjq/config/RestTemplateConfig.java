package com.gjq.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    @Qualifier("algorithmServiceRestTemplate")
    public RestTemplate algorithmServiceRestTemplate(
            RestTemplateBuilder builder,
            @Value("${external.algorithm-service.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${external.algorithm-service.read-timeout-ms:60000}") long readTimeoutMs
    ) {
        return buildRestTemplate(builder, connectTimeoutMs, readTimeoutMs);
    }

    @Bean
    @Qualifier("fileServiceRestTemplate")
    public RestTemplate fileServiceRestTemplate(
            RestTemplateBuilder builder,
            @Value("${external.file-service.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${external.file-service.read-timeout-ms:60000}") long readTimeoutMs
    ) {
        return buildRestTemplate(builder, connectTimeoutMs, readTimeoutMs);
    }

    private RestTemplate buildRestTemplate(RestTemplateBuilder builder, long connectTimeoutMs, long readTimeoutMs) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}
