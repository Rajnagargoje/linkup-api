package com.linkup.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.linkup")
public class LinkUpApplication {
    public static void main(String[] args) {
        SpringApplication.run(LinkUpApplication.class, args);
    }
}

