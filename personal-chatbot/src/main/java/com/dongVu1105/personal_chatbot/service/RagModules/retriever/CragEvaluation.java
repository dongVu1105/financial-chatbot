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
public class CragEvaluation {
    public enum DocumentQuality {
        GOOD,
        AMBIGUOUS,
        BAD
    }

    private DocumentQuality quality;
    private String reasoning;
    private String action;
    private List<Document> evaluatedDocuments;
    private String newQuery; // For AMBIGUOUS case
}

