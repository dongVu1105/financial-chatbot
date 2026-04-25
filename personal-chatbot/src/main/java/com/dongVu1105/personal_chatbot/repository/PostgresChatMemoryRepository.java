package com.dongVu1105.personal_chatbot.repository;


import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Repository
public class PostgresChatMemoryRepository implements ChatMemoryRepository {

    private final VectorStore chatMemoryStore;

    @Autowired
    public PostgresChatMemoryRepository(
            @Qualifier("chatMemoryVectorStore") VectorStore chatMemoryStore) {
        this.chatMemoryStore = chatMemoryStore;
    }

    @Override
    public List<String> findConversationIds() {
       return List.of();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<Document> results = chatMemoryStore.similaritySearch(
                SearchRequest.builder()
                        .query("dummy")   // Không rỗng
                        .topK(100)
                        .build()
        );
        return results.stream()
                .filter(doc -> conversationId.equals(doc.getMetadata().get("conversationId")))
                .map(this::documentToChatMessage)
                .collect(Collectors.toList());
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        messages.forEach(msg -> {
            Document doc = new Document(
                    msg.toString(),
                    Map.of(
                            "conversationId", conversationId,
                            "role", msg.getMessageType().name(),
                            "timestamp", Instant.now().toString()
                    )
            );
            chatMemoryStore.add(List.of(doc));
        });
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        var req = SearchRequest.builder()
                .query("")
                .topK(5000) // Lấy nhiều để xóa
                .filterExpression("conversationId == '" +
                        conversationId + "'")
                .build();
        List<Document> results = chatMemoryStore.similaritySearch(req);

        List<String> ids = results.stream()
                .map(Document::getId)
                .collect(Collectors.toList());

        if (!ids.isEmpty()) {
            chatMemoryStore.delete(ids);
        }
    }

    private Message documentToChatMessage(Document doc) {
        String content = doc.getText();
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