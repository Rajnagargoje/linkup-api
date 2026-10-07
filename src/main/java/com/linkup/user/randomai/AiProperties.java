package com.linkup.user.randomai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component @Getter @Setter
@PropertySource("classpath:ai-companions.properties")
@ConfigurationProperties(prefix = "linkup.ai")
public class AiProperties {
    private boolean enabled;
    private String apiKey = "";
    private String endpoint = "https://api.openai.com/v1/chat/completions";
    private String model = "gpt-4.1-mini";
    private int fallbackSeconds = 5;
    private int timeoutSeconds = 25;
    private int maxConcurrent = 12;
    private int maxOutputTokens = 220;
    private int dailyRequests = 500;
    private int userDailyRequests = 60;
    private double dailyBudgetUsd = 2;
    private double inputUsdPerMillion = .4;
    private double outputUsdPerMillion = 1.6;
    public boolean configured() { return enabled && apiKey != null && !apiKey.isBlank(); }
}
