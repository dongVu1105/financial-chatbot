package com.dongVu1105.personal_chatbot.service.RagModules;

import com.team14.chatbot.service.RagModules.retriever.RetrievalResponse;
import com.team14.chatbot.service.RagModules.retriever.RetrievalType;

import java.util.Map;

public interface RetrievalService {
    RetrievalResponse retrieveDocuments(String userInput, RetrievalType retrievalType, Map<String, Object> filterMetadata);
}
