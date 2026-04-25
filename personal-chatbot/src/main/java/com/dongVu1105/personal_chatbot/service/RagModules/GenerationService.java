package com.dongVu1105.personal_chatbot.service.RagModules;

import com.team14.chatbot.service.RagModules.generation.GenerationRequest;

/**
 * Unified service interface for all AI generation tasks.
 * Acts as a communication gateway, abstracting AI invocation, prompt
 * management,
 * and result processing for all other modules.
 * 
 * This service provides:
 * - Unified API for different task types
 * - Automatic model routing based on task type
 * - Prompt template management
 * - Context injection and template rendering
 * - Type-safe response parsing
 */
public interface GenerationService {

    /**
     * Generate content with typed response.
     * Parses the LLM response into the specified type.
     * 
     * @param request      The generation request
     * @param responseType The class type to parse response into
     * @param <T>          The response type
     * @return Parsed response of type T
     */
    <T> T generate(GenerationRequest request, Class<T> responseType);

    /**
     * Generate content based on the request.
     * Returns raw String response.
     * 
     * @param request The generation request containing task type, input, and
     *                context
     * @return String response from the LLM
     */
    String generate(GenerationRequest request);

}
