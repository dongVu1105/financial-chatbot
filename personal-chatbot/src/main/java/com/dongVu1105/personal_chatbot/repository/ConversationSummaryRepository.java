package com.dongVu1105.personal_chatbot.repository;

import com.team14.chatbot.entity.ConversationSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationSummaryRepository extends JpaRepository<ConversationSummary, String> {
    Optional<ConversationSummary> findByConversationId(String conversationId);
}
