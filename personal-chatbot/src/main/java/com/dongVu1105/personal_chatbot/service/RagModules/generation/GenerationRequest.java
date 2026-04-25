package com.dongVu1105.personal_chatbot.service.RagModules.generation;

import com.team14.chatbot.enums.TaskType;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.HashMap;
import java.util.Map;

/**
 * Request DTO for GenerationService.
 * Encapsulates all information needed for AI generation tasks.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GenerationRequest {

    Prompt prompt;

    /**
     * Optional: Override the default model selection for this task
     * If null, ModelRouter will choose the appropriate model based on taskType
     */
    Model specificModel;

    /**
     * Optional: Temperature setting (0.0 - 1.0)
     * If null, uses default from ModelRouter
     */
    Double temperature;
}
