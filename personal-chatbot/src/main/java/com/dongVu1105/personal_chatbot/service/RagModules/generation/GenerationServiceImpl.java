package com.dongVu1105.personal_chatbot.service.RagModules.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team14.chatbot.helper.ParseJsonHelper;
import com.team14.chatbot.service.RagModules.GenerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
// @RequiredArgsConstructor
@Slf4j
public class GenerationServiceImpl implements GenerationService {

    private final ChatClient geminiFlashClient;
    private final ChatClient geminiFlashLiteClient;
    private final ChatClient llamaCollabClient;

    private final ParseJsonHelper parseJsonHelper;

    public GenerationServiceImpl(
            @Qualifier("geminiFlashClient") ChatClient geminiFlashClient,
            @Qualifier("geminiFlashLiteClient") ChatClient geminiFlashLiteClient,

            @Qualifier("llamaCollabClient") ChatClient llamaCollabClient,
            ParseJsonHelper parseJsonHelper) {
        this.geminiFlashClient = geminiFlashClient;
        this.geminiFlashLiteClient = geminiFlashLiteClient;
        this.llamaCollabClient = llamaCollabClient;
        this.parseJsonHelper = parseJsonHelper;
    }

    @Override
    public <T> T generate(GenerationRequest request, Class<T> responseType) {
        try {
            Model model = request.getSpecificModel();
            Prompt prompt = request.getPrompt();

            log.info("Generating...");

            ChatClient chatClient;

            switch (model) {
                case GEMINI_2_5_FLASH:
                    chatClient = geminiFlashClient;
                    break;
                case GEMINI_2_5_FLASH_LITE:
                    chatClient = geminiFlashLiteClient;
                    break;
                default:
                    chatClient = llamaCollabClient;
                    ;
            }

//            chatClient = geminiFlashClient;

            String rawResponse = chatClient.prompt(prompt).call().content();

            log.info("Successfully generated");

            return parseJsonHelper.parseJsonResponse(rawResponse, responseType);
        } catch (

        Exception e) {
            log.error("Error parsing response to type {}: {}", responseType.getName(), e.getMessage());
            throw new RuntimeException("Failed to parse response: " + e.getMessage(), e);
        }
    }

    @Override
    public String generate(GenerationRequest request) {
        Model model = request.getSpecificModel();
        Prompt prompt = request.getPrompt();

        log.info("Generating...");

        try {
            ChatClient chatClient;

            switch (model) {
                case GEMINI_2_5_FLASH:
                    chatClient = geminiFlashClient;
                    break;
                case GEMINI_2_5_FLASH_LITE:
                    chatClient = geminiFlashLiteClient;
                    break;
                default:
                    chatClient = llamaCollabClient;
                    ;
            }

//            chatClient = geminiFlashClient;

            String rawResponse = chatClient.prompt(prompt).call().content();

            log.info("Successfully generated");

            return rawResponse;
        } catch (Exception e) {
            log.error("Error generating response: {}", e.getMessage());
            throw new RuntimeException("Failed to generate response: " + e.getMessage(), e);

        }

    }
}
