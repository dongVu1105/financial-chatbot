package com.dongVu1105.personal_chatbot.configuration;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.ApiKey;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;

@Configuration
@EnableScheduling
public class ChatClientConfig {

    private static final String GEMINI_FLASH = "gemini-2.0-flash";
    private static final String GEMINI_FLASH_LITE = "gemini-2.0-flash-lite";

    @Bean
    public MyCustomChatModel myCustomChatModel(RestClient.Builder builder) {

        // SỬA LẠI: Dùng builder() static từ interface ChatOptions
        ChatOptions defaultOptions = ChatOptions.builder()
                .model("Meta-Llama-3.1-8B")
                .temperature(0.5)
                .build();

        return new MyCustomChatModel(
                builder,
                "https://unapprovable-bryon-subpeltately.ngrok-free.dev/v1/chat/completions",
                defaultOptions
        );
    }
    @Bean("llamaCollabClient")
    public ChatClient llamaCollabClient(MyCustomChatModel myCustomChatModel) {
        return ChatClient.builder(myCustomChatModel)
                .build();
    }

    @Bean("geminiFlashClient")
    public ChatClient geminiFlashClient(GoogleGenAiChatModel model) {
        return ChatClient.builder(model)
                .defaultOptions(GoogleGenAiChatOptions.builder()
                        .model(GEMINI_FLASH)
                        .build())
                .build();
    }

    @Bean("geminiFlashLiteClient")
    public ChatClient geminiFlashLiteClient(GoogleGenAiChatModel model) {
        return ChatClient.builder(model)
                .defaultOptions(GoogleGenAiChatOptions.builder()
                        .model(GEMINI_FLASH_LITE)
                        .build())
                .build();
    }

//    @Bean
//    public WebClient.Builder webClientBuilder() {
//        return WebClient.builder();
//    }

    @Bean
    public OllamaApi ollamaApi() {
        return OllamaApi.builder()
                .baseUrl("http://localhost:11434")
                .build();
    }

    @Bean
    public OllamaChatModel ollamaLlamaModel(OllamaApi ollamaApi) {
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaChatOptions.builder()
                        .model("llama3.2:1b")
                        .build())
                .build();
    }

    @Bean("llama3_2_1b")
    public ChatClient llama3_2_1b(OllamaChatModel ollamaLlamaModel) {
        return ChatClient.create(ollamaLlamaModel);
    }

}