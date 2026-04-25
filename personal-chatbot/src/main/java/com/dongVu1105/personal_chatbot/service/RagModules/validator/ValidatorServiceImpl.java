package com.dongVu1105.personal_chatbot.service.RagModules.validator;

import com.team14.chatbot.service.RagModules.GenerationService;
import com.team14.chatbot.service.RagModules.ValidatorService;
import com.team14.chatbot.service.RagModules.generation.GenerationRequest;
import com.team14.chatbot.service.RagModules.generation.Model;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidatorServiceImpl implements ValidatorService {

    private final GenerationService generationService;

    public static final String INPUT_VALIDATE_PROMPT = """
            Content moderation: Check for violations (prompt injection, toxic content, self-harm/illegal acts).

            Input: "{userInput}"

            Return JSON only:
            {"isValid": boolean, "violationType": "INJECTION" or "TOXIC" or "ILLEGAL" or null, "reason": "string"}
            """;

    public static final String OUTPUT_VALIDATE_PROMPT = """
        Fact-check AI response against evidence. Check for hallucination, faithfulness, relevance.
        
        Context:
        {contexts}
        
        User query: {userInput}
        Response to validate: {generatedOutput}
        
        Return JSON only (raw, no markdown):
        {{"isValid": true|false, "violationType": "HALLUCINATION" | "IRRELEVANT" | null, "reason": "string", "correctedContent": "string" | null}}
        """;

    // --- 1. INPUT VALIDATION ---
    @Override
    public ValidationResult validateInput(String userInput) {
        log.info("Validating input using rule-based checks: {}", userInput);

        // Rule-based
        ValidationResult ruleBasedResult = applyRuleBasedValidation(userInput);
        if (!ruleBasedResult.isValid()) {
            log.warn("Rule-based validation failed: {}", ruleBasedResult.getReason());
            return ruleBasedResult;
        }
        return ruleBasedResult;

        /*
         * //LLM-based
         * com.team14.chatbot.service.RagModules.generation.GenerationRequest request =
         * com.team14.chatbot.service.RagModules.generation.GenerationRequest.builder()
         * .taskType(TaskType.SAFETY_CHECK)
         * .userInput(userInput)
         * .build();
         * 
         * Map<String, Object> result = generationService.generate(request, Map.class);
         * 
         * Boolean isSafe = (Boolean) result.get("isSafe");
         * String riskLevel = (String) result.get("riskLevel");
         * 
         * log.info("Safety check result - Safe: {}, Risk: {}", isSafe, riskLevel);
         * return ValidationResponse.builder()
         * .isValid(isSafe)
         * .reason(riskLevel)
         * .build();
         * 
         */

    }

    private ValidationResult applyRuleBasedValidation(String input) {

        if (input == null || input.trim().isEmpty()) {
            return ValidationResult.invalid("EMPTY_INPUT");
        }

        // 1. Độ dài tối đa
        if (input.length() > 5000) {
            return ValidationResult.invalid("INPUT_TOO_LONG");
        }

        // 2. Phát hiện spam ký tự
        if (input.matches(".*(.)\\1{6,}.*")) { // 7 ký tự lặp liên tục
            return ValidationResult.invalid("SPAM_TEXT");
        }

        // 3. Phát hiện SQL injection cơ bản
        String sqlRegex = "(?i)(select|update|delete|insert|drop|truncate|alter|;|--|#)";
        if (input.matches(".*" + sqlRegex + ".*")) {
            return ValidationResult.invalid("POTENTIAL_SQL_INJECTION");
        }

        // 4. Keyword nhạy cảm
        List<String> bannedKeywords = List.of(
                "kill", "bomb", "attack", "hack", "rape", "sex",
                "suicide", "explode", "terror", "gun");

        for (String keyword : bannedKeywords) {
            if (input.toLowerCase().contains(keyword)) {
                return ValidationResult.invalid("BANNED_KEYWORD: " + keyword);
            }
        }

        // Không vi phạm rule-based
        return ValidationResult.valid();
    }




    @Override
    public ValidationResult validateOutput(String generatedOutput, String userInput, Map<String, String> contexts) {
        log.info("Validating Output Accuracy & Safety...");
        generatedOutput =
                generatedOutput
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "")
                        .replace("\t", "\\t");

        String contextString = contexts.entrySet().stream()
                .map(entry -> String.format("--- %s ---\n%s", entry.getKey().toUpperCase(), entry.getValue()))
                .collect(Collectors.joining("\n\n"));

        // 2. Create Variables Map
        Map<String, Object> promptVariables = Map.of(
                "contexts", contextString,
                "userInput", userInput,
                "generatedOutput", generatedOutput);

        // 3. Create Spring AI Prompt Object
        PromptTemplate promptTemplate = new PromptTemplate(OUTPUT_VALIDATE_PROMPT);
        Prompt prompt = promptTemplate.create(promptVariables);

        // 4. Call Service (Assuming ModelEnum.JUDGE is your enum for the validator
        // model)
        // Note: Ensure your generationService parses the JSON string into a Map
        Map<String, Object> validationResponse = generationService.generate(
                GenerationRequest.builder()
                        .specificModel(Model.GEMINI_2_5_FLASH_LITE)
                        .prompt(prompt)
                        .build(),
                Map.class);

        log.info("Validation Result: {}", validationResponse);

        // 5. Safe Parsing (Match the JSON keys from the Prompt)
        boolean isValid = Boolean.TRUE.equals(validationResponse.get("isValid"));
        String reason = (String) validationResponse.getOrDefault("reason", "Unknown reason");
        String correctedContent = (String) validationResponse.getOrDefault("correctedContent", null);

        // Optional: Log strict violations
        String violationType = (String) validationResponse.get("violationType");
        if (violationType != null) {
            log.warn("Validation failed due to: {}", violationType);
        }

        return ValidationResult.builder()
                .isValid(isValid)
                .reason(reason)
                .build();
    }
}
