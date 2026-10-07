package com.linkup.user.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class EmailHttpConfig {

    @Bean
    public RestClient resendClient(RestClient.Builder builder) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(20));

        return builder
                .baseUrl("https://api.resend.com")
                .requestFactory(factory)
                .build();
    }
}