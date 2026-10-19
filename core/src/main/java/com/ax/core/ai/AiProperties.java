package com.ax.core.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ax.ai")
public record AiProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
