package com.logsense.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClientAiConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    public ChatClient chatClient(@Value("${spring.ai.model.chat:ollama}") String provider,
                                 ObjectProvider<OpenAiChatModel> openai,
                                 ObjectProvider<OllamaChatModel> ollama) {
        ChatModel model = "openai".equalsIgnoreCase(provider) ? openai.getIfAvailable() : ollama.getIfAvailable();
        if (model == null) {
            return null;
        }
        return ChatClient.builder(model).build();
    }
}
