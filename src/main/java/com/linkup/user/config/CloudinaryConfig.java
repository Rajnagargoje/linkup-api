package com.linkup.user.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

@Configuration
public class CloudinaryConfig {
    @Bean
    @ConditionalOnProperty(name = "app.upload.provider", havingValue = "cloudinary")
    public Cloudinary cloudinary(@Value("${CLOUDINARY_CLOUD_NAME}") String cloud,
                                 @Value("${CLOUDINARY_API_KEY}") String key,
                                 @Value("${CLOUDINARY_API_SECRET}") String secret) {
        if (cloud.isBlank() || key.isBlank() || secret.isBlank())
            throw new IllegalStateException("Cloudinary credentials must be configured.");
        return new Cloudinary(Map.of("cloud_name", cloud, "api_key", key, "api_secret", secret,
            "secure", true, "connection_timeout", 10000, "socket_timeout", 30000));
    }
}
