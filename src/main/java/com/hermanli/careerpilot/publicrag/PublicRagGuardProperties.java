package com.hermanli.careerpilot.publicrag;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConfigurationProperties(prefix = "careerpilot.public-rag.guard")
public class PublicRagGuardProperties {

    private boolean enabled;
    private int dailyUserRequestLimit = 3;
    private int dailyGlobalRequestLimit = 100;
    private int maxConcurrentRequests = 2;
    private int maxInputTokens = 6_000;
    private int maxOutputTokens = 800;
    private int maxTotalTokens = 6_800;
    private String identityHmacSecret = "";

    @PostConstruct
    void validate() {
        if (!enabled) {
            return;
        }
        if (dailyUserRequestLimit <= 0
                || dailyGlobalRequestLimit < dailyUserRequestLimit
                || maxConcurrentRequests <= 0
                || maxInputTokens <= 0
                || maxOutputTokens <= 0
                || maxTotalTokens < maxInputTokens + maxOutputTokens) {
            throw new IllegalStateException("Invalid public RAG guard limits.");
        }
        if (identityHmacSecret == null
                || identityHmacSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "CAREERPILOT_PUBLIC_RAG_GUARD_HMAC_SECRET must contain at least 32 UTF-8 bytes."
            );
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDailyUserRequestLimit() {
        return dailyUserRequestLimit;
    }

    public void setDailyUserRequestLimit(int dailyUserRequestLimit) {
        this.dailyUserRequestLimit = dailyUserRequestLimit;
    }

    public int getDailyGlobalRequestLimit() {
        return dailyGlobalRequestLimit;
    }

    public void setDailyGlobalRequestLimit(int dailyGlobalRequestLimit) {
        this.dailyGlobalRequestLimit = dailyGlobalRequestLimit;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    public int getMaxInputTokens() {
        return maxInputTokens;
    }

    public void setMaxInputTokens(int maxInputTokens) {
        this.maxInputTokens = maxInputTokens;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getMaxTotalTokens() {
        return maxTotalTokens;
    }

    public void setMaxTotalTokens(int maxTotalTokens) {
        this.maxTotalTokens = maxTotalTokens;
    }

    public String getIdentityHmacSecret() {
        return identityHmacSecret;
    }

    public void setIdentityHmacSecret(String identityHmacSecret) {
        this.identityHmacSecret = identityHmacSecret;
    }
}
