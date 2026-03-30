package com.dongVu1105.personal_chatbot.repository;


import com.dongVu1105.personal_chatbot.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, String> {
    List<Document> findByUserId(String userId);
    List<Document> findByStatus(Document.DocumentStatus status);
}
