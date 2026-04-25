package com.dongVu1105.personal_chatbot.service.RagModules.retriever;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.document.Document;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalResponse {
    private List<Document> documents;
    private Integer totalRetrieved;
    private String retrievalStrategy;
    private CragEvaluation cragEvaluation;
}

