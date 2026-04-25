package com.dongVu1105.personal_chatbot.service.RagModules.query_processor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team14.chatbot.enums.QueryIntent;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Query Processing Service - Core component for intelligent query handling
 * 
 * This service implements three main stages:
 * 1. Query Routing: Classifies user intent and decides processing pipeline
 * 2. Query Transformation: Applies step-back prompting and HyDE
 * 3. Query Expansion: Generates multiple query variations for better retrieval
 */
@Service
// @RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class QueryProcessingService {

    ChatClient chatClient;
    ChatClient geminiFlashClient;
    ObjectMapper objectMapper;

    public QueryProcessingService(
            @Qualifier("geminiFlashLiteClient") ChatClient chatClient,
            @Qualifier("geminiFlashClient") ChatClient geminiFlashClient,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.geminiFlashClient = geminiFlashClient;
        this.objectMapper = objectMapper;
    }

    // ==================== HIGH LEVEL ORCHESTRATION ====================

    /**
     * High level execute method that combines in ONE LLM call:
     * - intent analysis (multi-intent)
     * - step-back rewriting
     * - HyDE hypothetical document generation
     */
    public QueryProcessingResult execute(String query, String conversationHistory) {
        log.info("Executing combined query processing for: {}", query);

        String prompt = String.format(EXECUTE_COMBINED_PROMPT, query,
                conversationHistory != null ? conversationHistory : "");

        try {
            String response = geminiFlashClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            String clean = response.trim()
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();

            if (clean.isEmpty()) {
                log.warn("Combined execute returned empty response, falling back to simple intent routing only");
                // Fallback: just run intent analysis, no step-back / HyDE
                List<IntentTask> intents = analyzeIntent(query, conversationHistory);
                return new QueryProcessingResult(intents, null, null);
            }

            // Parse to a lightweight DTO (inner class) and then map to
            // QueryProcessingResult
            CombinedExecuteRaw raw = objectMapper.readValue(clean, CombinedExecuteRaw.class);

            List<IntentTask> intents = raw.intents() != null ? raw.intents()
                    : analyzeIntent(query, conversationHistory);
            String stepBack = raw.step_back_question();
            String hydeDoc = raw.hyde_document();

            return new QueryProcessingResult(intents, stepBack, hydeDoc);
        } catch (Exception e) {
            log.error("Error in combined execute, falling back to intent-only", e);
            List<IntentTask> intents = analyzeIntent(query, conversationHistory);
            return new QueryProcessingResult(intents, null, null);
        }
    }

    /**
     * Internal DTO used only for parsing the JSON of the combined execute prompt.
     * Field names must match JSON keys from EXECUTE_COMBINED_PROMPT.
     */
    private record CombinedExecuteRaw(
            List<IntentTask> intents,
            String step_back_question,
            String hyde_document) {
    }

    // ==================== PROMPTS ====================

    private static final String QUERY_ROUTING_PROMPT = """
            Classify user query intent in the financial domain. A query may have multiple intents.

            Query: "%s"

            Valid intents:
            Financial:
            - KNOWLEDGE_QUERY: Ask for definitions or factual financial information.
            - ADVISORY: Ask for advice, recommendations, or opinions on financial decisions.
            - CALCULATION: Ask to calculate or compute financial values.
            - UNSUPPORTED: Financial-related but unclear, incomplete, or out of scope.

            Non-financial:
            - MALICIOUS_CONTENT: Illegal, fraudulent, or harmful financial requests.
            - NON_FINANCIAL: Not related to finance.

            Return JSON array only (no markdown):
            [{"intent":"INTENT","query":"QUERY","explanation":"SHORT_REASON"}]
            """;

    private static final String STEP_BACK_PROMPT = """
            You are refining a vague or short question.

            Task:
            - Identify the general intent behind the question.
            - Rewrite it to be clearer and more understandable.
            - Keep the question high-level and neutral.
            - Do NOT add specific numbers, products, scenarios, or assumptions.
            - Do NOT narrow the scope unnecessarily.

            Original question:
            "%s"

            Return only the rewritten question in Vietnamese.
            """;

    private static final String HYDE_PROMPT = """
            Generate a 3-5 sentence hypothetical document excerpt that directly answers the query. Use financial terminology and reference-style writing.

            Query: "%s"

            Return only the document excerpt in Vietnamese, no title or explanation.
            """;

    private static final String MULTI_QUERY_PROMPT = """
            Generate %d query variations with different wording/keywords but same meaning for search optimization.

            Original: "%s"

            Return numbered list (1., 2., 3...) in Vietnamese, queries only, no explanation.
            """;

    private static final String ADVISORY_PLANNING_PROMPT = """
            You are a senior financial assistant.

            Your task is to deeply analyze the following user query and prepare
            everything needed for a high‑quality advisory answer in a RAG pipeline.

            User query:
            %s

            You MUST perform internally (do NOT explain these steps in the output):
            1) Step‑back reasoning: rewrite the query into a clearer, more general question
               that captures the core financial problem.
            2) HyDE: imagine a short hypothetical document (3‑8 Vietnamese sentences)
               that would perfectly answer this query.
            3) Multi‑query expansion: generate exactly %d Vietnamese search sub‑queries
               that cover different angles/keywords of the same problem.

            Return a SINGLE JSON object only (no markdown, no comments, no extra text)
            with the following fields:
            - step_back_question: string          // rewritten question (in Vietnamese)
            - hyde_document: string              // hypothetical answer document (in Vietnamese)
            - sub_queries: [string]              // list of exactly %d Vietnamese search queries
            """;

    private static final String ADVISORY_ANALYSIS_PROMPT = """
            You are a senior financial expert. Analyze the following advisory query and provide a comprehensive structured analysis.

            User query:
            %s

            Return a SINGLE JSON object only (no markdown, no comments, no extra text) with the following structure:
            {
              "advisory_type": "string - type of advisory (e.g., investment, savings, loan, insurance, etc.)",
              "knowledge_level": "string - assumed financial knowledge level (beginner, intermediate, advanced)",
              "primary_objective": "string - main objective/goal of the user",
              "time_horizon": "string - time horizon for the advisory (short-term, medium-term, long-term)",
              "risk_tolerance": "string - assumed risk tolerance level (conservative, moderate, aggressive)",
              "key_risks": [
                {
                  "risk": "string - description of the risk",
                  "severity": "low | medium | high",
                  "description": "string - detailed description of the risk",
                  "evidence_refs": ["string"] // optional references to evidence
                }
              ],
              "potential_benefits": [
                {
                  "benefit": "string - description of the benefit",
                  "conditions": "string - conditions under which this benefit applies"
                }
              ],
              "suitable_when": ["string - conditions when this is suitable"],
              "not_suitable_when": ["string - conditions when this is NOT suitable"],
              "regulatory_sensitivity": true | false
            }
            """;

    private static final String EXECUTE_COMBINED_PROMPT = """
            You are an expert financial assistant and query router.

            Your task is to perform ALL of the following in ONE step for the given user query:

            1) Intent analysis (multi-intent allowed)
               - Classify the query into the SAME intent set as below.
               - Each detected intent must be returned as an object in the "intents" array.

               Valid intents: [KNOWLEDGE_QUERY|ADVISORY|CALCULATION|UNSUPPORTED|MALICIOUS_CONTENT|NON_FINANCIAL]
               Financial:
               - KNOWLEDGE_QUERY: Ask for definitions or factual financial information.
               - ADVISORY: Ask for advice, recommendations, or opinions on financial decisions.
               - CALCULATION: Ask to calculate or compute financial values.
               - UNSUPPORTED: Financial-related but unclear, incomplete, or out of scope.

               Non-financial:
               - MALICIOUS_CONTENT: Illegal, fraudulent, or harmful financial requests.
               - NON_FINANCIAL: Not related to finance.

            2) Step-back rewriting
               - Rewrite the query into a clearer, more general Vietnamese question
                 that captures the core financial problem.
               - Keep it neutral, do NOT add specific numbers/products/assumptions.

            3) HyDE hypothetical document
               - Imagine a short Vietnamese document (3-8 sentences)
                 that would perfectly answer the query.

            User query:
            "%s"

            Conversation history (may be empty, use only if relevant):
            "%s"

            Return a SINGLE JSON object only (no markdown, no comments, no extra text)
            with the following structure:
            {
              "intents": [
                {
                  "intent": "INTENT_NAME",
                  "query": "original or refined query for this intent",
                  "explanation": "short Vietnamese explanation why this intent was chosen"
                }
              ],
              "step_back_question": "Vietnamese rewritten general question",
              "hyde_document": "Vietnamese hypothetical document (3-8 sentences)"
            }
            """;

    /**
     * Routes the query by classifying user intent.
     * Determines whether RAG pipeline is needed or direct LLM response is
     * sufficient.
     */
    public List<IntentTask> analyzeIntent(String query, String conversationHistory) {
        log.debug("Routing query: {}", query);

        String prompt = String.format(QUERY_ROUTING_PROMPT, query);

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return parseRoutingResponse(response, query);
        } catch (Exception e) {
            log.error("Error in query routing, defaulting to NON_FINANCIAL", e);
            return List.of(new IntentTask(QueryIntent.NON_FINANCIAL, query,
                    "Không thể phân tích câu hỏi, mặc định NON_FINANCIAL"));
        }
    }

    /**
     * Parses the JSON response from the routing LLM call.
     */
    private List<IntentTask> parseRoutingResponse(String response, String query) {
        try {
            // 1. Clean markdown
            String cleanResponse = response.trim()
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();

            if (cleanResponse.isEmpty() || cleanResponse.equals("[]"))
                return List.of();

            return objectMapper.readValue(cleanResponse, new TypeReference<List<IntentTask>>() {
            });
        } catch (Exception e) {
            log.error("Failed to parse routing response, defaulting to NON_FINANCIAL", e);
            return List.of(new IntentTask(QueryIntent.NON_FINANCIAL, query,
                    "Không thể phân tích câu hỏi, mặc định NON_FINANCIAL"));
        }
    }

    /**
     * Applies Step-Back Prompting to transform the query.
     * Creates a more abstract, general version of the query that captures the
     * underlying concept.
     */
    public String transformWithStepBack(String query) {
        log.info("Applying step-back prompting to: {}", query);

        String prompt = String.format(STEP_BACK_PROMPT, query);

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return response.trim();
        } catch (Exception e) {
            log.error("Error in step-back transformation", e);
            return query; // Return original query on error
        }
    }

    /**
     * Generates a Hypothetical Document using HyDE technique.
     * The generated document contains relevant keywords and concepts for better
     * semantic search.
     */
    public String generateHypotheticalDocument(String query) {
        log.info("Generating hypothetical document for: {}", query);

        String prompt = String.format(HYDE_PROMPT, query);

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return response.trim();
        } catch (Exception e) {
            log.error("Error in HyDE generation", e);
            return null;
        }
    }

    // ==================== STAGE 3: QUERY EXPANSION ====================

    /**
     * Expands the query into multiple variations using Multi-Query technique.
     * Each variation emphasizes different aspects or uses alternative keywords.
     */
    public List<String> expandQueryForExecutor(String query, int count) {
        log.info("Expanding query: {} into {} variations", query, count);

        String prompt = String.format(MULTI_QUERY_PROMPT, count, query);

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return parseExpandedQueries(response);
        } catch (Exception e) {
            log.error("Error in query expansion", e);
            return List.of(query); // Return original query on error
        }
    }

    /**
     * Parses the numbered list of expanded queries from LLM response.
     */
    private List<String> parseExpandedQueries(String response) {
        List<String> queries = new ArrayList<>();
        String[] lines = response.split("\n");

        for (String line : lines) {
            // Remove numbering (1., 2., 3., etc.) and clean up
            String cleaned = line.trim()
                    .replaceFirst("^\\d+\\.?\\s*", "")
                    .replaceFirst("^-\\s*", "")
                    .trim();

            if (!cleaned.isEmpty()) {
                queries.add(cleaned);
            }
        }

        return queries;
    }

    // ==================== STAGE 2.5: ADVISORY PLANNING (COMBINED)
    // ====================

    /**
     * For ADVISORY intent, run a single planning prompt that performs:
     * - Step-back rewriting
     * - HyDE hypothetical document generation
     * - Multi-query expansion
     * and returns all data in one JSON object.
     */
    public AdvisoryPlanningResult planAdvisoryQuery(String query, int multiQueryCount) {
        log.info("Planning advisory query for: {} | multiQueryCount={}", query, multiQueryCount);

        int safeCount = Math.max(1, multiQueryCount);
        String prompt = String.format(ADVISORY_PLANNING_PROMPT, query, safeCount, safeCount);

        try {
            String response = geminiFlashClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            String cleanResponse = response.trim()
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();

            if (cleanResponse.isEmpty()) {
                log.warn("Advisory planning returned empty response, falling back to null");
                return null;
            }

            AdvisoryPlanningResult result = objectMapper.readValue(cleanResponse, AdvisoryPlanningResult.class);
            log.debug("Advisory planning result: {}", result);
            return result;
        } catch (Exception e) {
            log.error("Error in advisory planning, falling back to classic processing", e);
            return null;
        }
    }

    // ==================== ADVISORY ANALYSIS ====================

    /**
     * Analyzes an advisory query as a financial expert and returns structured
     * analysis.
     * This analysis will be used to generate a neutral, informative response.
     */
    public AdvisoryAnalysisResult analyzeAdvisoryQuery(String query) {
        log.info("Analyzing advisory query: {}", query);

        String prompt = String.format(ADVISORY_ANALYSIS_PROMPT, query);

        try {
            String response = geminiFlashClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            String cleanResponse = response.trim()
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();

            if (cleanResponse.isEmpty()) {
                log.warn("Advisory analysis returned empty response, falling back to null");
                return null;
            }

            AdvisoryAnalysisResult result = objectMapper.readValue(cleanResponse, AdvisoryAnalysisResult.class);
            log.debug("Advisory analysis result: {}", result);
            return result;
        } catch (Exception e) {
            log.error("Error in advisory analysis, falling back to null", e);
            return null;
        }
    }

}
