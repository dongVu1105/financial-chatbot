package com.dongVu1105.personal_chatbot.repository;

import com.team14.chatbot.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {
    Page<Message> findAllByConversationId(String conversationId, Pageable pageable);
    
    @Query("SELECT m FROM Message m WHERE m.conversationId = :conversationId ORDER BY m.createdAt DESC")
    List<Message> findRecentByConversationId(@Param("conversationId") String conversationId, Pageable pageable);
}
