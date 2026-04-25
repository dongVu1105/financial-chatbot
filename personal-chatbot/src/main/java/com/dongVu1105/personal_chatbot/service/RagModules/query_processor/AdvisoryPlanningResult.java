package com.dongVu1105.personal_chatbot.service.RagModules.query_processor;

import java.util.List;

/**
 * Combined planning result for ADVISORY intent.
 * Parsed directly from the JSON returned by the LLM.
 */
public record AdvisoryPlanningResult(
        String step_back_question,
        String hyde_document,
        List<String> sub_queries) {
}


