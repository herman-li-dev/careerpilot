package com.hermanli.careerpilot.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Disables every DashScope auto-configuration before bean discovery when AI is not explicitly enabled.
 * The Alibaba starter version used by this project does not consistently honor its per-model enabled flags.
 */
public class AiOptionalEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String DASH_SCOPE_AUTO_CONFIGURATIONS = String.join(",",
            "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration",
            "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAgentAutoConfiguration",
            "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeImageAutoConfiguration",
            "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioSpeechAutoConfiguration",
            "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioTranscriptionAutoConfiguration",
            "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeRerankAutoConfiguration",
            "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeEmbeddingAutoConfiguration"
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("careerpilot.ai.enabled", Boolean.class, false)) {
            environment.getPropertySources().addFirst(new MapPropertySource(
                    "careerpilotAiOptionalAutoConfiguration",
                    Map.of("spring.autoconfigure.exclude", DASH_SCOPE_AUTO_CONFIGURATIONS)
            ));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
