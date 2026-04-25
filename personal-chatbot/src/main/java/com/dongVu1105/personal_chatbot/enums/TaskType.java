package com.dongVu1105.personal_chatbot.enums;

/**
 * Enum representing different types of tasks that can be performed by the
 * GenerationService.
 * Each task type corresponds to a specific AI generation operation with its own
 * prompt template.
 */
public enum TaskType {
    // 1. For QueryProcessor
    /**
     * Analyze user intent and extract entities from the query
     */
    ANALYZE_INTENT,

    // 2. For Planner (Orchestrator)
    /**
     * Generate execution plan (JSON list of steps)
     */
    GENERATE_PLAN,

    // 3. For Executor (Orchestrator)
    /**
     * Explain a concept or term
     */
    EXPLAIN_TERM,

    /**
     * Summarize documents from Retriever
     */
    SUMMARIZE_DOCS,

    /**
     * Interpret calculation results
     */
    INTERPRET_CALCULATION,

    /**
     * Plan calculation steps and expression
     */
    CALCULATION_PLANNING,

    // 4. For Validator
    /**
     * Safety check for toxic/spam content
     */
    SAFETY_CHECK,

    /**
     * Cross-check correctness (LLM-as-a-Judge)
     */
    JUDGE_CROSS_CHECK,

    /**
     * Fusion nhiều câu trả lời (sub-query hoặc multi-intent)
     */
    FUSION,

    /**
     * Self-correct / regenerate sau bước fusion khi validator fail
     */
    FUSION_SELF_CORRECT
}
