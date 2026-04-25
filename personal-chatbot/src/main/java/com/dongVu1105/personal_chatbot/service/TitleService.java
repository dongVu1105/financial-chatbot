package com.dongVu1105.personal_chatbot.service;

import com.team14.chatbot.entity.Conversation;
import com.team14.chatbot.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
//@RequiredArgsConstructor
public class TitleService {

    private final ConversationRepository conversationRepository;
    private final ChatClient chatClient;

    public TitleService(
            ConversationRepository conversationRepository,
            @Qualifier("geminiFlashLiteClient") ChatClient chatClient
    ) {
        this.conversationRepository = conversationRepository;
        this.chatClient = chatClient;
    }

    public String generateTitle(String conversationId, String userMessage) {
        try {
            // Check if conversation exists first
            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null) {
                return "Untitled Analysis";
            }

            String promptText = String.format(
                    "Summarize the following user message into a short, concise conversation title (max 5 words) in Vietnamese. Do not use quotes.\nUser: %s\nTitle:",
                    userMessage);

            String generatedTitle = chatClient.prompt().user(promptText).call().content();

            // Sanitize
            if (generatedTitle != null) {
                generatedTitle = generatedTitle.replace("\"", "").replace("'", "").trim();
                // Update DB
                conversation.setTitle(generatedTitle);
                conversationRepository.save(conversation);
                return generatedTitle;
            }
        } catch (Exception e) {
            System.err.println("Error generating title: " + e.getMessage());
        }
        return "Untitled Analysis";
    }
}
