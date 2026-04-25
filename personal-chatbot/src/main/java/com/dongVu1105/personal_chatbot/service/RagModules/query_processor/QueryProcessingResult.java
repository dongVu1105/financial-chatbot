package com.dongVu1105.personal_chatbot.service.RagModules.query_processor;

import java.util.List;

/**
 * Aggregated result of query processing, combining:
 * - intent routing
 * - step-back rewritten question
 * - HyDE hypothetical document
 */
public record QueryProcessingResult(
        List<IntentTask> intents,
        String stepBackQuestion,
        String hydeDocument) {
}


