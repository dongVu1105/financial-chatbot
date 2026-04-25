package com.dongVu1105.personal_chatbot.service.RagModules.retriever;

import com.team14.chatbot.service.RagModules.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class QueryRetrievalService implements RetrievalService {

    private final HybridSearchService hybridSearchService;
    private final MetadataFilterService metadataFilterService;
    private final ReRankerService reRankerService;
    private final CragService cragService;

    @Value("${retrieval.hybrid-top-k:50}")
    private int hybridTopK;

    @Value("${retrieval.rerank-top-k:30}")
    private int rerankTopK;

    @Value("${retrieval.final-top-k:5}")
    private int finalTopK;

    @Value("${retrieval.enable-crag:true}")
    private boolean enableCrag;

    @Value("${crag.max-depth:2}")
    private int maxDepth;

    /**
     * Main retrieval method - full pipeline
     * 
     * @param query          The search query
     * @param filterMetadata Optional metadata filters
     * @return RetrievalResponse with documents
     */
    @Override
    public RetrievalResponse retrieveDocuments(String query, RetrievalType retrievalType,
            Map<String, Object> filterMetadata) {

        // Step 1: Hybrid Search → Top 50 candidates
        List<Document> hybridResults = hybridSearchService.hybridSearch(query, retrievalType, hybridTopK);
        log.info("Hybrid search returned {} documents", hybridResults.size());
        log.info("Hybrid search returned {} documents", hybridResults);


        // Step 2: Metadata Filter
        List<Document> filteredResults = metadataFilterService.filterDocuments(hybridResults, filterMetadata);
        log.info("After filtering: {} documents", filteredResults.size());

        log.info("After filtering: {} documents", filteredResults);

        // Step 3: Re-Ranker → Top 30 → Top 5
        List<Document> rerankedResults;
        if (filteredResults.size() > rerankTopK) {
            // Take top 30 for re-ranking
            List<Document> top30 = filteredResults.stream()
                    .limit(rerankTopK)
                    .toList();
            rerankedResults = reRankerService.rerank(query, top30);
        } else {
            rerankedResults = reRankerService.rerank(query, filteredResults);
        }
        log.info("After re-ranking: {} documents", rerankedResults.size());

        // Step 4: CRAG Evaluation (if enabled)
        CragEvaluation cragEvaluation = null;
        List<Document> finalDocuments = rerankedResults;
        // Without CRAG, just take top K
        finalDocuments = rerankedResults.stream()
                .limit(finalTopK)
                .toList();

        return RetrievalResponse.builder()
                .documents(finalDocuments)
                .totalRetrieved(finalDocuments.size())
                .build();
    }


    boolean shouldRerank(String query, List<Document> docs) {

        if (docs.size() <= 5) return false;

        double top1 = docs.get(0).getScore();
        double top2 = docs.get(1).getScore();

        // vector search đủ tự tin
        if (top1 - top2 >= 0.15) return false;

        // còn lại → rerank
        return true;
    }
    /**
     * Retrieve documents with CRAG evaluation
     * 
     * @param query The search query
     * @return RetrievalResponse with documents and CRAG evaluation
     */
    public RetrievalResponse retrieveWithCrag(String query) {
        return retrieveDocuments(query, null, null, true, 0);
    }

    /**
     * Internal retrieval method with depth tracking for recursive calls
     */
    private RetrievalResponse retrieveDocuments(String query, Map<String, Object> filterMetadata,
            RetrievalType retrievalType,
            boolean useCrag, int depth) {
        if (depth > maxDepth) {
            log.warn("Max depth reached for retrieval, returning empty results");
            return RetrievalResponse.builder()
                    .documents(Collections.emptyList())
                    .totalRetrieved(0)
                    .retrievalStrategy("MAX_DEPTH_REACHED")
                    .build();
        }

        log.info("Retrieval pipeline - Step 1: Hybrid Search (depth={})", depth);

        // Step 1: Hybrid Search → Top 50 candidates
        List<Document> hybridResults = hybridSearchService.hybridSearch(query, retrievalType, hybridTopK);
        log.info("Hybrid search returned {} documents", hybridResults.size());

        // Step 2: Metadata Filter
        List<Document> filteredResults = metadataFilterService.filterDocuments(hybridResults, filterMetadata);
        log.info("After filtering: {} documents", filteredResults.size());

        // Step 3: Re-Ranker → Top 30 → Top 5
        List<Document> rerankedResults;
        if (filteredResults.size() > rerankTopK) {
            // Take top 30 for re-ranking
            List<Document> top30 = filteredResults.stream()
                    .limit(rerankTopK)
                    .toList();
            rerankedResults = reRankerService.rerank(query, top30);
        } else {
            rerankedResults = reRankerService.rerank(query, filteredResults);
        }
        log.info("After re-ranking: {} documents", rerankedResults.size());

        // Step 4: CRAG Evaluation (if enabled)
        CragEvaluation cragEvaluation = null;
        List<Document> finalDocuments = rerankedResults;

        if (useCrag && !rerankedResults.isEmpty()) {
            log.info("Retrieval pipeline - Step 4: CRAG Evaluation");

            // Take top 5 for CRAG evaluation
            List<Document> top5ForCrag = rerankedResults.stream()
                    .limit(finalTopK)
                    .toList();

            cragEvaluation = cragService.evaluateDocuments(query, top5ForCrag);
            log.info("CRAG evaluation: quality={}, action={}",
                    cragEvaluation.getQuality(), cragEvaluation.getAction());

            // Handle CRAG decision
            finalDocuments = handleCragDecision(query, cragEvaluation, top5ForCrag, depth);
        } else {
            // Without CRAG, just take top K
            finalDocuments = rerankedResults.stream()
                    .limit(finalTopK)
                    .toList();
        }

        return RetrievalResponse.builder()
                .documents(finalDocuments)
                .totalRetrieved(finalDocuments.size())
                .retrievalStrategy(useCrag ? "HYBRID_FILTER_RERANK_CRAG" : "HYBRID_FILTER_RERANK")
                .cragEvaluation(cragEvaluation)
                .build();
    }

    /**
     * Handle CRAG decision and return appropriate documents
     */
    private List<Document> handleCragDecision(String query, CragEvaluation evaluation,
            List<Document> documents, int depth) {
        return switch (evaluation.getQuality()) {
            case GOOD -> {
                // Send to generation
                log.info("CRAG: Documents are GOOD, sending to generation");
                yield documents;
            }
            case AMBIGUOUS -> {
                // Active retrieval: create new query and search again
                log.info("CRAG: Documents are AMBIGUOUS, performing active retrieval");
                String newQuery = evaluation.getNewQuery();
                if (newQuery == null || newQuery.isEmpty()) {
                    // Generate query from ambiguous document
                    newQuery = generateQueryFromDocument(query, documents.get(0));
                }

                // Recursive retrieval with new query
                RetrievalResponse recursiveResponse = retrieveDocuments(
                        newQuery, null, null, true, depth + 1);

                // Combine original and new results
                List<Document> combined = new ArrayList<>(documents);
                combined.addAll(recursiveResponse.getDocuments());
                yield combined.stream()
                        .distinct()
                        .limit(finalTopK)
                        .toList();
            }
            case BAD -> {
                // Skip RAG, return empty (will use LLM knowledge)
                log.info("CRAG: Documents are BAD, skipping RAG");
                yield Collections.emptyList();
            }
        };
    }

    /**
     * Generate new query from ambiguous document
     */
    private String generateQueryFromDocument(String originalQuery, Document document) {
        // Simple heuristic: combine original query with key terms from document
        String docText = document.getText();
        // Extract first sentence or key phrase
        String keyPhrase = docText.length() > 100
                ? docText.substring(0, 100)
                : docText;

        return originalQuery + " " + keyPhrase;
    }

    /**
     * Active retrieval when CRAG identifies ambiguous documents
     * 
     * @param query        Original query
     * @param ambiguousDoc The ambiguous document
     * @return New retrieval results
     */
    public RetrievalResponse activeRetrieval(String query, Document ambiguousDoc) {
        log.info("Performing active retrieval for ambiguous document");

        // Generate new query from ambiguous document
        String newQuery = generateQueryFromDocument(query, ambiguousDoc);

        // Perform retrieval with new query
        return retrieveDocuments(newQuery, null, null, true, 0);
    }

    /**
     * Simple retrieval without CRAG (for backward compatibility)
     */
    public List<Document> simpleRetrieve(String query, int topK) {
        List<Document> hybridResults = hybridSearchService.hybridSearch(query, null, topK);
        List<Document> filteredResults = metadataFilterService.filterDocuments(hybridResults, null);

        if (filteredResults.size() > rerankTopK) {
            List<Document> top30 = filteredResults.stream()
                    .limit(rerankTopK)
                    .toList();
            return reRankerService.rerank(query, top30);
        } else {
            return reRankerService.rerank(query, filteredResults);
        }
    }
}
