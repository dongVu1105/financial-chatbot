package com.dongVu1105.personal_chatbot.service.RagModules.retriever;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReRankerService {

    private final WebClient.Builder webClientBuilder;

    @Value("${cohere.api.key:}")
    private String cohereApiKey;

    @Value("${cohere.rerank.model:rerank-multilingual-v3.0}")
    private String rerankModel;

    @Value("${cohere.rerank.top-n:5}")
    private int topN;

    private static final String COHERE_RERANK_URL = "https://api.cohere.ai/v1/rerank";
    private static final int MAX_DOCUMENTS_TO_RERANK = 30;

    /**
     * Re-rank documents using Cohere Rerank API
     * @param query The search query
     * @param documents List of documents to re-rank (max 30)
     * @return Top N re-ranked documents
     */
    public List<Document> rerank(String query, List<Document> documents) {
        if (documents.isEmpty()) {
            return Collections.emptyList();
        }

        if (cohereApiKey == null || cohereApiKey.isEmpty()) {
            log.warn("Cohere API key not configured, returning original documents");
            return documents.stream().limit(topN).collect(Collectors.toList());
        }

        // Limit to max documents
        List<Document> documentsToRerank = documents.stream()
                .limit(MAX_DOCUMENTS_TO_RERANK)
                .collect(Collectors.toList());

        try {
            log.debug("Re-ranking {} documents with Cohere", documentsToRerank.size());

            // Prepare request
            CohereRerankRequest request = new CohereRerankRequest();
            request.setModel(rerankModel);
            request.setQuery(query);
            request.setDocuments(documentsToRerank.stream()
                    .map(Document::getText)
                    .collect(Collectors.toList()));
            request.setTopN(topN);

            // Call Cohere API
            WebClient webClient = webClientBuilder
                    .baseUrl(COHERE_RERANK_URL)
                    .defaultHeader("Authorization", "Bearer " + cohereApiKey)
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .build();

            CohereRerankResponse response = webClient.post()
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(CohereRerankResponse.class)
//                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
//                            .filter(throwable -> throwable instanceof Exception))
                    .block(Duration.ofSeconds(10));

            if (response == null || response.getResults() == null) {
                log.warn("Cohere rerank returned null response, returning original documents");
                return documentsToRerank.stream().limit(topN).collect(Collectors.toList());
            }

            // Map results back to documents
            List<Document> rerankedDocuments = new ArrayList<>();
            for (CohereRerankResult result : response.getResults()) {
                int index = result.getIndex();
                if (index >= 0 && index < documentsToRerank.size()) {
                    Document doc = documentsToRerank.get(index);
                    // Update metadata with rerank score
                    doc.getMetadata().put("rerank_score", result.getRelevanceScore());
                    doc.getMetadata().put("rerank_index", result.getIndex());
                    rerankedDocuments.add(doc);
                }
            }

            log.debug("Re-ranked {} documents, returning top {}", rerankedDocuments.size(), topN);
            return rerankedDocuments;

        } catch (Exception e) {
            log.error("Error calling Cohere rerank API", e);
            // Fallback: return original documents
            return documentsToRerank.stream().limit(topN).collect(Collectors.toList());
        }
    }

    // DTOs for Cohere API
    @Data
    private static class CohereRerankRequest {
        private String model;
        private String query;
        private List<String> documents;
        @JsonProperty("top_n")
        private Integer topN;
    }

    @Data
    private static class CohereRerankResponse {
        private String id;
        private List<CohereRerankResult> results;
    }

    @Data
    private static class CohereRerankResult {
        private Integer index;
        @JsonProperty("relevance_score")
        private Double relevanceScore;
    }
}

