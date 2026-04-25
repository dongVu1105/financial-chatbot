package com.dongVu1105.personal_chatbot.service.RagModules;

import com.team14.chatbot.service.RagModules.query_processor.IntentTask;

public interface QueryProcessingService {
    IntentTask analyzeIntent(String query, String conversationHistory);
}
