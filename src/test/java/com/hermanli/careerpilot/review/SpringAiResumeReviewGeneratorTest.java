package com.hermanli.careerpilot.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermanli.careerpilot.publicrag.PublicRagGuardProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiResumeReviewGeneratorTest {

    @Test
    void sendsTheGuardedOutputLimitAsAnExplicitProviderOption() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("{\"suggestions\":[]}"))
        )));
        PublicRagGuardProperties properties = new PublicRagGuardProperties();
        properties.setMaxOutputTokens(321);
        SpringAiResumeReviewGenerator generator = new SpringAiResumeReviewGenerator(
                chatModel,
                new ObjectMapper(),
                properties
        );

        generator.generate(new ResumeReviewModelRequest(List.of()));

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getOptions().getMaxTokens()).isEqualTo(321);
    }
}
