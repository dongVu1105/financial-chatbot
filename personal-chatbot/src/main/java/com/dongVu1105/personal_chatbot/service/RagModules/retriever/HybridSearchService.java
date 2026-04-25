package com.dongVu1105.personal_chatbot.service.RagModules.retriever;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class HybridSearchService {

    @Qualifier("knowledgeVectorStore")
    private final VectorStore knowledgeVectorStore;
    @Qualifier("caseStudiesVectorStore")
    private final VectorStore caseStudiesVectorStore;
    private final Bm25IndexService bm25IndexService;

    private static final int DENSE_TOP_K = 10;
    private static final int SPARSE_TOP_K = 10;
    private static final int RRF_K = 10; // RRF constant

    /**
     * Perform hybrid search: Dense + Sparse + RRF Fusion
     * 
     * @param query The search query
     * @param topK  Number of results to return after RRF
     * @return List of documents ranked by RRF score
     */
    public List<Document> hybridSearch(String query, RetrievalType retrievalType, int topK) {
        log.info("Performing hybrid search for query: {}", query);

        // Step 1: Dense Retrieval (Embeddings)
        List<Document> denseResults = denseRetrieval(query, retrievalType, DENSE_TOP_K);
        log.info("Dense retrieval returned {} documents", denseResults.size());

        // Step 2: Sparse Retrieval (BM25)
        List<Document> sparseResults = sparseRetrieval(query, retrievalType, SPARSE_TOP_K);
        log.info("Sparse retrieval returned {} documents", sparseResults.size());

        // Step 3: RRF Fusion
        List<Document> fusedResults = rrfFusion(denseResults, sparseResults, topK);
        log.info("RRF fusion returned {} documents", fusedResults.size());

        return fusedResults;
    }

    /**
     * Dense retrieval using vector similarity search
     */
    private List<Document> denseRetrieval(String query, RetrievalType retrievalType, int topK) {
        try {
            switch (retrievalType) {
                case KNOWLEDGE_RETRIEVE:
                    return knowledgeVectorStore.similaritySearch(
                            SearchRequest.builder()
                                    .query(query)
                                    .topK(topK)
//                                    .similarityThreshold(0.65)
                                    .build());

                case CASE_STUDIES_RETRIEVE:
                    return caseStudiesVectorStore.similaritySearch(
                            SearchRequest.builder()
                                    .query(query)
                                    .topK(topK)
//                                    .similarityThreshold(0.65)

                                    .build());

                default:
                    return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error in dense retrieval", e);
            return Collections.emptyList();
        }
    }

    /**
     * Sparse retrieval using BM25
     */
    private List<Document> sparseRetrieval(String query, RetrievalType retrievalType, int topK) {
        try {
            return bm25IndexService.search(query, retrievalType, topK);
        } catch (Exception e) {
            log.error("Error in sparse retrieval", e);
            return Collections.emptyList();
        }
    }

    /**
     * Reciprocal Rank Fusion (RRF) algorithm
     * Formula: RRF(d) = Σ 1/(k + rank_i(d))
     * 
     * @param denseResults  Results from dense retrieval
     * @param sparseResults Results from sparse retrieval
     * @param topK          Number of results to return
     * @return Fused and ranked documents
     */
    private List<Document> rrfFusion(List<Document> denseResults, List<Document> sparseResults, int topK) {
        // Create a map to store RRF scores for each document
        Map<String, RrfScore> documentScores = new HashMap<>();

        // Process dense results
        for (int rank = 0; rank < denseResults.size(); rank++) {
            Document doc = denseResults.get(rank);
            String docId = getDocumentId(doc);
            documentScores.computeIfAbsent(docId, k -> new RrfScore(doc))
                    .addDenseScore(1.0 / (RRF_K + rank + 1));
        }

        // Process sparse results
        for (int rank = 0; rank < sparseResults.size(); rank++) {
            Document doc = sparseResults.get(rank);
            String docId = getDocumentId(doc);
            documentScores.computeIfAbsent(docId, k -> new RrfScore(doc))
                    .addSparseScore(1.0 / (RRF_K + rank + 1));
        }

        // Sort by total RRF score and return top K
        return documentScores.values().stream()
                .sorted(Comparator.comparingDouble(RrfScore::getTotalScore).reversed())
                .limit(topK)
                .map(RrfScore::getDocument)
                .collect(Collectors.toList());
    }

    /**
     * Get unique document ID from document metadata or content hash
     */
    private String getDocumentId(Document doc) {
        // Try to get ID from metadata
        Object id = doc.getMetadata().get("id");
        if (id != null) {
            return id.toString();
        }

        // Fallback: use content hash
        return String.valueOf(doc.getText().hashCode());
    }

    /**
     * Helper class to track RRF scores for a document
     */
    private static class RrfScore {
        private final Document document;
        private double denseScore = 0.0;
        private double sparseScore = 0.0;

        public RrfScore(Document document) {
            this.document = document;
        }

        public void addDenseScore(double score) {
            this.denseScore += score;
        }

        public void addSparseScore(double score) {
            this.sparseScore += score;
        }

        public double getTotalScore() {
            return denseScore + sparseScore;
        }

        public Document getDocument() {
            // Update metadata with RRF scores
            document.getMetadata().put("rrf_score", getTotalScore());
            document.getMetadata().put("dense_score", denseScore);
            document.getMetadata().put("sparse_score", sparseScore);
            return document;
        }
    }
}
