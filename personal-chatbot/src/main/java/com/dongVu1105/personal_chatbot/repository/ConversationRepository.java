package com.dongVu1105.personal_chatbot.repository;

import com.team14.chatbot.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {
    Page<Conversation> findAllByUserId (String userId, Pageable pageable);
}
