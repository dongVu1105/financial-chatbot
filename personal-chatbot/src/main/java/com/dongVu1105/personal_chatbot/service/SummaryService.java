package com.dongVu1105.personal_chatbot.service;

import com.team14.chatbot.entity.ConversationSummary;
import com.team14.chatbot.entity.Message;
import com.team14.chatbot.repository.ConversationSummaryRepository;
import com.team14.chatbot.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
// @RequiredArgsConstructor
@Slf4j
public class SummaryService {
    private static final int RECENT_MESSAGES_LIMIT = 5;
    private static final String SUMMARY_PROMPT = """
            Update conversation summary. Keep important context and user goals, remove unnecessary details, be concise. Don't add assumptions or information not in conversation.

            Current summary:
            {current_summary}

            Recent messages:
            {recent_messages}

            Return updated summary in Vietnamese only.
            """;

    private final ChatClient chatClient;
    private final MessageRepository messageRepository;
    private final ConversationSummaryRepository summaryRepository;

    public SummaryService(
            @Qualifier("geminiFlashLiteClient") ChatClient chatClient,
            MessageRepository messageRepository,
            ConversationSummaryRepository summaryRepository) {
        this.chatClient = chatClient;
        this.messageRepository = messageRepository;
        this.summaryRepository = summaryRepository;
    }

    @Async
    public void updateSummaryAsync(String conversationId) {
        try {
            log.info("Update Summary async ...");
            // Get recent messages (last 5)
            List<Message> recentMessages = messageRepository
                    .findRecentByConversationId(conversationId, PageRequest.of(0, RECENT_MESSAGES_LIMIT));

            if (recentMessages.isEmpty()) {
                return;
            }

            // Get current summary if exists
            String currentSummary = summaryRepository.findByConversationId(conversationId)
                    .map(ConversationSummary::getSummary)
                    .orElse("");

            // Format recent messages
            StringBuilder messagesText = new StringBuilder();
            for (int i = recentMessages.size() - 1; i >= 0; i--) {
                Message msg = recentMessages.get(i);
                messagesText.append(String.format("%s: %s\n", msg.getRole(), msg.getText()));
            }

            // Create prompt with template
            Map<String, Object> params = new HashMap<>();
            params.put("current_summary", currentSummary);
            params.put("recent_messages", messagesText.toString());

            // Generate new summary
            String newSummary = chatClient.prompt()
                    .user(userSpec -> userSpec
                            .text(SUMMARY_PROMPT)
                            .params(params))
                    .call()
                    .content();

            // Save or update summary
            ConversationSummary summary = summaryRepository.findByConversationId(conversationId)
                    .map(s -> {
                        s.setSummary(newSummary);
                        return s;
                    })
                    .orElse(ConversationSummary.builder()
                            .conversationId(conversationId)
                            .summary(newSummary)
                            .build());

            summaryRepository.save(summary);
            log.info("Updated summary successfully");

        } catch (Exception e) {
            log.error("Error updating summary for conversation: " + conversationId, e);
            // Fail silently as per requirements
        }
    }

    public String getSummary(String conversationId) {
        return summaryRepository.findByConversationId(conversationId)
                .map(ConversationSummary::getSummary)
                .orElse("");
    }
}
