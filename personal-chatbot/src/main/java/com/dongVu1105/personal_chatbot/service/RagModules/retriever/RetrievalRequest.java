package com.dongVu1105.personal_chatbot.service.RagModules.retriever;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalRequest {
    private String query;
    private Map<String, Object> filterMetadata;
    private Integer topK;
    private Boolean enableCrag;
    private RetrievalType retrievalType;
}

