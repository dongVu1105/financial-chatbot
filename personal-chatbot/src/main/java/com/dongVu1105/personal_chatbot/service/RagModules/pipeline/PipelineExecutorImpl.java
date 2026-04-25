package com.dongVu1105.personal_chatbot.service.RagModules.pipeline;

import com.team14.chatbot.service.RagModules.generation.GenerationRequest;
import com.team14.chatbot.service.RagModules.CalculatorService;
import com.team14.chatbot.service.RagModules.GenerationService;
import com.team14.chatbot.service.RagModules.calculator.CalculationResult;
import com.team14.chatbot.service.RagModules.PipelineExecutorService;
import com.team14.chatbot.service.RagModules.generation.Model;
import com.team14.chatbot.service.RagModules.query_processor.QueryProcessingService;
import com.team14.chatbot.service.RagModules.retriever.QueryRetrievalService;
import com.team14.chatbot.service.RagModules.retriever.RetrievalRequest;
import com.team14.chatbot.service.RagModules.retriever.RetrievalResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;

import com.team14.chatbot.service.RagModules.query_processor.AdvisoryAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Slf4j
@RequiredArgsConstructor
public class PipelineExecutorImpl implements PipelineExecutorService {

    // private final QueryRetrievalService retrievalService;
    private final CalculatorService calculatorService;
    private final GenerationService generationService;
    private final QueryRetrievalService queryRetrievalService;
    private final QueryProcessingService queryProcessingService;
    private final ObjectMapper objectMapper;

    // Prompt templates
    private static final String CALCULATION_PLANNING_PROMPT = """
            Extract the mathematical expression from the following financial query
            and think step by step about how to compute the answer.

            Query:
            {query}

            Return JSON only with the following shape (do NOT add extra fields):
            expression: string    // the valid math expression that can be evaluated directly
            reasoningSteps: [string] // ordered list of short reasoning steps in English
            """;

    private static final String INTERPRET_CALCULATION_PROMPT = """
            You are a professional financial assistant.

            Task:
            - Based on the analysis and calculation results below, answer the user's question.
            - Explain step by step: what is being calculated, which formula is used, how the numbers are plugged in,
              and what the final numerical result is.
            - Prioritize simple, intuitive explanations suitable for non‑experts in finance.

            User question:
            {query}

            Calculation analysis and result (input for you, do NOT repeat verbatim):
            {calculationReasoning}

            Requirements:
            - Respond in Vietnamese only.
            - Be concrete and specific; avoid unnecessary theory.
            - When mentioning numbers, always include appropriate units (e.g. đồng, %, tháng, ...).
            """;

    private static final String KB_EXPLANATION_PROMPT = """
            You are a professional financial assistant.

            Task:
            - Answer the question by EXPLAINING the information in the knowledge base.
            
            - Explain concepts, rules, and implications in detail, not just definitions.

            Constraints:
            - Use ONLY the provided knowledge base.
            - If information is missing, clearly state that.
            - Only answer question related to finance

            User question:
            "{query}"

            Knowledge base:
            {documents}

            Output:
            - Vietnamese only.
            - Prefer detailed, multi-paragraph explanation over brevity.
            """;

    private static final String ADVISORY_GENERATION_PROMPT = """
            Using the advisory analysis below, generate a clear and neutral explanation
            for a retail user.

            Rules:
            - Do NOT give direct recommendations.
            - Explicitly mention NOT SUITABLE conditions.
            - Reflect uncertainty if confidence_level is low.
            - Use ONLY information from the analysis JSON.
            - DO NOT introduce new risks, benefits, or conclusions.
            - Use conditional language ("if", "when", "not suitable if").
            - Avoid persuasive or directive wording.

            User question:
            {query}

            Knowledge base:
            {documents}

            Advisory analysis:
            {analysis_json}

            Output:
            - Vietnamese only.
            - Be neutral and informative, not directive.
            """;

    @Override
    public String execute(PipelinePlan plan) {

        // 0. Check Fast Path
        if (plan.getDirectResponse() != null) {
            return plan.getDirectResponse();
        }

            // intentPipeline: xử lý tuần tự 1 query duy nhất theo cấu hình trong
        // PipelinePlan.
        // Các bước intent routing, step-back, HyDE, multi-query... đã được xử lý
        // trước ở QueryProcessing / Planner.
        String pipelineQuery = plan.getPipelineQuery() != null ? plan.getPipelineQuery() : plan.getQuery();
        if ("CALCULATION".equalsIgnoreCase(plan.getIntent()) || "NON_FINANCIAL".equalsIgnoreCase(plan.getIntent())) {
            pipelineQuery = plan.getQuery();
        }
        AdvisoryAnalysisResult advisoryAnalysis = null;

        // For ADVISORY intent we still run structured analysis, but without changing
        // the pipeline query here. The analysis result is used only in generation.
        if ("ADVISORY".equalsIgnoreCase(plan.getIntent())) {
            advisoryAnalysis = queryProcessingService.analyzeAdvisoryQuery(plan.getQuery());
            if (advisoryAnalysis != null) {
                log.info("Advisory analysis completed | advisory_type: {} | confidence_level: {}",
                        advisoryAnalysis.advisory_type(), advisoryAnalysis.confidence_level());
            } else {
                log.warn("Advisory analysis returned null, proceeding without analysis");
            }
        }

        log.info("Executing single-query pipeline | intent={} | query={}", plan.getIntent(), pipelineQuery);
        return runSinglePipeline(plan, pipelineQuery, advisoryAnalysis);
    }

    // Multi-query, step-back, HyDE đã được xử lý ở QueryProcessing / Planner.
    // Executor chỉ chạy tuần tự: Retrieval -> Calculation -> Generation cho 1
    // query.
    private String runSinglePipeline(PipelinePlan plan, String query,
            AdvisoryAnalysisResult advisoryAnalysis) {
        StringBuilder ctx = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        long stepStartTime;

        // Retrieval
        if (plan.getRetrievalConfig() != null) {
            stepStartTime = System.currentTimeMillis();
            var rCfg = plan.getRetrievalConfig();
            log.debug("[Pipeline] Starting retrieval for: {} | topK: {}", query, rCfg.getTopK());

            RetrievalRequest req = RetrievalRequest.builder()
                    .query(query)
                    .topK(rCfg.getTopK())
                    .build();
            RetrievalResponse docs = queryRetrievalService.retrieveDocuments(req.getQuery(), rCfg.getRetrievalType(),
                    null);

            long retrievalDuration = System.currentTimeMillis() - stepStartTime;
            if (!docs.getDocuments().isEmpty()) {
                ctx.append("=== KNOWLEDGE BASE ===\n");
                ctx.append("[Query] ").append(query).append("\n");
                String docsStr = docs.getDocuments().stream()
                        .map(d -> "- " + d.getText())
                        .collect(Collectors.joining("\n"));
                ctx.append(docsStr).append("\n\n");
                log.debug("[Pipeline] Retrieval completed in {}ms | Documents found: {} | Query: {}",
                        retrievalDuration, docs.getDocuments().size(), query);
            } else {
                log.debug("[Pipeline] Retrieval completed in {}ms | No documents found | Query: {}",
                        retrievalDuration, query);
            }
        }

        // Calculation
        if (plan.getCalculationConfig() != null && plan.getCalculationConfig().isCalculationNeeded()) {
            stepStartTime = System.currentTimeMillis();
            log.debug("[Pipeline] Starting calculation step | Query: {}", query);

            String expression = null;

            try {
                log.debug("[Pipeline] No expression provided, extracting from query using LLM");
                long planningStartTime = System.currentTimeMillis();

                // Create prompt for calculation planning
                PromptTemplate planningTemplate = new PromptTemplate(CALCULATION_PLANNING_PROMPT);
                Map<String, Object> planningVars = Map.of("query", query);
                Prompt planningPrompt = planningTemplate.create(planningVars);

                GenerationRequest planReq = GenerationRequest.builder()
                        .prompt(planningPrompt)
                        .specificModel(Model.GEMINI_2_5_FLASH)
                        .build();

                @SuppressWarnings("unchecked")
                Map<String, Object> analysisResult = generationService.generate(planReq, Map.class);
                long planningDuration = System.currentTimeMillis() - planningStartTime;
                log.debug("[Pipeline] Calculation planning completed in {}ms | Result: {}",
                        planningDuration, analysisResult);

                if (analysisResult != null) {
                    String extractedExpr = (String) analysisResult.get("expression");
                    if (extractedExpr != null && !extractedExpr.isBlank()) {
                        expression = extractedExpr;

                        // Add reasoning steps if available
                        if (analysisResult.containsKey("reasoningSteps")) {
                            @SuppressWarnings("unchecked")
                            List<String> steps = (List<String>) analysisResult.get("reasoningSteps");
                            if (steps != null && !steps.isEmpty()) {
                                reasoning.append("Problem analysis:\n");
                                reasoning.append(String.join("\n", steps)).append("\n");
                            }
                        }
                    }
                }

                // If we have a valid expression, perform the calculation
                if (expression != null && !expression.isBlank()) {
                    ctx.append("=== CALCULATION ANALYSIS ===\n");
                    ctx.append("Query: ").append(query).append("\n");
                    ctx.append("Expression: ").append(expression).append("\n\n");

                    long calcStartTime = System.currentTimeMillis();
                    try {
                        // Call calculator service with the expression string directly
                        log.debug("[Pipeline] Evaluating expression: {}", expression);
                        CalculationResult result = calculatorService.calculate(expression);
                        long calcDuration = System.currentTimeMillis() - calcStartTime;

                        if (result.isSuccess()) {
                            ctx.append("=== CALCULATION RESULT ===\n");
                            ctx.append("Expression: ").append(expression).append("\n");
                            ctx.append("Result: ").append(result.getValue()).append("\n\n");

                            // Add to reasoning if not already added
                            if (reasoning.length() == 0) {
                                reasoning.append("1. Query analysis: ").append(query).append("\n");
                                reasoning.append("2. Mathematical expression: ").append(expression).append("\n");
                            }
                            reasoning.append("3. Calculation result: ").append(result.getValue()).append("\n");
                            log.debug("[Pipeline] Calculation successful in {}ms | Expression: {} | Result: {}",
                                    calcDuration, expression, result.getValue());
                        } else {
                            ctx.append("=== CALCULATION ERROR ===\n");
                            ctx.append("Cannot evaluate expression: ").append(expression).append("\n\n");
                            reasoning.append("Error: Cannot evaluate expression: ").append(expression).append("\n");
                            log.warn("[Pipeline] Calculation failed in {}ms | Expression: {}", expression);
                        }
                    } catch (Exception e) {
                        long calcDuration = System.currentTimeMillis() - calcStartTime;
                        log.error("[Pipeline] Calculation exception in {}ms | Expression: {} | Duration: {}ms",
                                expression, calcDuration, e);
                        ctx.append("=== CALCULATION EXECUTION ERROR ===\n");
                        ctx.append("Error occurred: ").append(e.getMessage()).append("\n\n");
                        reasoning.append("Error: ").append(e.getMessage()).append("\n");
                    }
                } else {
                    ctx.append("=== CANNOT DETERMINE EXPRESSION ===\n\n");
                    reasoning.append("Cannot determine calculation expression from query.\n");
                    log.warn("[Pipeline] Cannot determine calculation expression from query");
                }

                long calculationTotalDuration = System.currentTimeMillis() - stepStartTime;
                log.debug("[Pipeline] Calculation step completed in {}ms | Query: {}", calculationTotalDuration,
                        query);
            } catch (Exception e) {
                long calculationTotalDuration = System.currentTimeMillis() - stepStartTime;
                log.error("[Pipeline] Calculation planning error in {}ms | Query: {}", calculationTotalDuration,
                        query,
                        e);
                ctx.append("=== CALCULATION PLANNING ERROR ===\n");
                ctx.append("Error analyzing query: ").append(e.getMessage()).append("\n\n");
                reasoning.append("Analysis error: ").append(e.getMessage()).append("\n");
            }
        }

        // Generation
        var generationConfig = plan.getGenerationConfig();
        if (generationConfig != null) {
            stepStartTime = System.currentTimeMillis();
            log.debug("[Pipeline] Starting generation step | Query: {} | Model: {}",
                    query, generationConfig.getModel());

            String finalContext = ctx.toString();
            if (finalContext.isEmpty()) {
                finalContext = "No additional data. Answer based on your knowledge.";
                log.debug("[Pipeline] No context from retrieval/calculation, using default");
            } else {
                log.debug("[Pipeline] Context length: {} chars", finalContext.length());
            }

            // Build prompt based on whether we have calculation, advisory analysis, or
            // standard KB
            PromptTemplate promptTemplate;
            Map<String, Object> promptVars = new HashMap<>();
            promptVars.put("query", query);
            promptVars.put("documents", finalContext);

            boolean hasCalculation = plan.getCalculationConfig() != null && reasoning.length() > 0;
            boolean hasAdvisoryAnalysis = advisoryAnalysis != null && "ADVISORY".equalsIgnoreCase(plan.getIntent());

            if (hasCalculation) {
                // Use INTERPRET_CALCULATION prompt
                promptTemplate = new PromptTemplate(INTERPRET_CALCULATION_PROMPT);
                promptVars.put("calculationReasoning", reasoning.toString());
                log.debug("[Pipeline] Using INTERPRET_CALCULATION prompt | Reasoning length: {}", reasoning.length());
            } else if (hasAdvisoryAnalysis) {
                // Use ADVISORY_GENERATION prompt with analysis JSON
                try {
                    String analysisJson = objectMapper.writeValueAsString(advisoryAnalysis);
                    promptTemplate = new PromptTemplate(ADVISORY_GENERATION_PROMPT);
                    promptVars.put("analysis_json", analysisJson);
                    log.debug("[Pipeline] Using ADVISORY_GENERATION prompt | Analysis JSON length: {}",
                            analysisJson.length());
                } catch (Exception e) {
                    log.error(
                            "[Pipeline] Failed to serialize advisory analysis to JSON, falling back to KB_EXPLANATION",
                            e);
                    promptTemplate = new PromptTemplate(KB_EXPLANATION_PROMPT);
                }
            } else {
                // Use SUMMARIZE_DOCS prompt
                promptTemplate = new PromptTemplate(KB_EXPLANATION_PROMPT);
                log.debug("[Pipeline] Using KB_EXPLANATION prompt");
            }

            Prompt prompt = promptTemplate.create(promptVars);

            GenerationRequest genReq = GenerationRequest.builder()
                    .prompt(prompt)
                    .specificModel(Model.GEMINI_2_5_FLASH)
                    .temperature(generationConfig.getTemperature())
                    .build();

            String result = generationService.generate(genReq);
            long generationDuration = System.currentTimeMillis() - stepStartTime;
            log.debug("[Pipeline] Generation completed in {}ms | Query: {} | Result length: {}",
                    generationDuration, query, result != null ? result.length() : 0);

            return result;
        }

        log.debug("[Pipeline] No generation config, returning context only | Query: {}", query);
        return ctx.toString();
    }

}
