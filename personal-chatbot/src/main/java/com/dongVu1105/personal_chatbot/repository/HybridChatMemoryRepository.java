package com.dongVu1105.personal_chatbot.repository;


import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.document.Document;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Primary
public class HybridChatMemoryRepository implements ChatMemoryRepository {

    private final InMemoryChatMemoryRepository inMemory = new InMemoryChatMemoryRepository();
    private final PostgresChatMemoryRepository postgres;

    public HybridChatMemoryRepository(PostgresChatMemoryRepository postgres) {
        this.postgres = postgres;
    }

    @Override
    public List<String> findConversationIds() {
        return List.of();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        System.out.println("note");
//        List<Message> mem = inMemory.findByConversationId(conversationId);
//        if (!mem.isEmpty()) return mem;
        List<Message> db = postgres.findByConversationId(conversationId);
//        if (!db.isEmpty()) inMemory.saveAll(conversationId, db);
        return db;
//        return results.stream()
//                .map(this::documentToChatMessage)
//                .collect(Collectors.toList());
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        inMemory.saveAll(conversationId, messages);
        postgres.saveAll(conversationId, messages);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
//        inMemory.deleteByConversationId(conversationId);
//        postgres.deleteByConversationId(conversationId);
    }

    private Message documentToChatMessage(Document doc) {
        String content = doc.getFormattedContent();
        String role = (String) doc.getMetadata().get("role");
        if (MessageType.USER.name().equals(role)) {
            return new UserMessage(content);
        } else if (MessageType.ASSISTANT.name().equals(role)) {
            return new AssistantMessage(content);
        } else if (MessageType.SYSTEM.name().equals(role)) {
            return new SystemMessage(content);
        } else {
            // Xử lý trường hợp mặc định
            throw new IllegalArgumentException("Unknown message role: " + role);
        }
    }
}
