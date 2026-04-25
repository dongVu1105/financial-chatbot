package com.dongVu1105.personal_chatbot.service.RagModules.query_processor;

import com.team14.chatbot.enums.QueryIntent;

/**
 * Record to hold routing result data
 */
public record IntentTask(
                QueryIntent intent,
                String query,
                String explanation) {
}