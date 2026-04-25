package com.dongVu1105.personal_chatbot.service.RagModules.query_processor;

import java.util.List;

/**
 * Advisory analysis result from financial expert analysis.
 * Contains structured analysis of the advisory query including risks, benefits,
 * and suitability.
 */
public record AdvisoryAnalysisResult(
        String advisory_type,
        String assumed_persona,
        String knowledge_level,
        String primary_objective,
        String time_horizon,
        String risk_tolerance,
        List<RiskItem> key_risks,
        List<BenefitItem> potential_benefits,
        List<String> suitable_when,
        List<String> not_suitable_when,
        String confidence_level, // "low" | "medium" | "high"
        Boolean regulatory_sensitivity) {
    public record RiskItem(
            String risk,
            String severity, // "low" | "medium" | "high"
            String description,
            List<String> evidence_refs) {
    }

    public record BenefitItem(
            String benefit,
            String conditions) {
    }
}
