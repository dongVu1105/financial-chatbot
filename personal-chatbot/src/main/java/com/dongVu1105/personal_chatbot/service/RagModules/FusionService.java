package com.dongVu1105.personal_chatbot.service.RagModules;

import java.util.List;

/**
 * Service interface for fusing multiple responses into a single coherent
 * answer.
 * Handles fusion of:
 * - Multiple intent responses (from RagService)
 * - Multiple sub-query responses (from PipelineExecutor)
 */
public interface FusionService {

    /**
     * Fuse multiple responses into a single coherent answer.
     * 
     * @param originalQuery The original user query
     * @param responses     List of responses to fuse
     * @param model         Optional model override (null uses default)
     * @return Fused response
     */
    String fuse(String originalQuery, List<String> responses,
            com.team14.chatbot.service.RagModules.generation.Model model);

    /**
     * Fuse with self-correction mode (used when validation fails).
     * 
     * @param originalQuery   The original user query
     * @param responses       List of responses to fuse
     * @param validatorReason Reason from validator for why previous fusion failed
     * @param mode            Mode of correction ("self-correct", "retry_1",
     *                        "retry_2", etc.)
     * @param model           Optional model override (null uses default)
     * @return Fused and corrected response
     */
    String fuseWithCorrection(String originalQuery, List<String> responses, String validatorReason, String mode,
            com.team14.chatbot.service.RagModules.generation.Model model);
}
