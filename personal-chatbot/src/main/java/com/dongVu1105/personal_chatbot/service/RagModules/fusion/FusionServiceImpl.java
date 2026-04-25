package com.dongVu1105.personal_chatbot.service.RagModules.fusion;

import com.team14.chatbot.service.RagModules.FusionService;
import com.team14.chatbot.service.RagModules.GenerationService;
import com.team14.chatbot.service.RagModules.generation.GenerationRequest;
import com.team14.chatbot.service.RagModules.generation.Model;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FusionServiceImpl implements FusionService {

    private final GenerationService generationService;

    private static final String FUSION_PROMPT = """
            Merge multiple responses into one coherent answer. Remove duplicates, organize logically, ensure accuracy.

            Query: "{query}"
            Responses:
            {answers}

            Return merged answer in Vietnamese only.
            """;

    private static final String FUSION_SELF_CORRECT_PROMPT = """
            Merge and correct responses based on validator feedback. Fix accuracy issues, remove duplicates, reorganize logic.

            Query: "{query}"
            Responses:
            {answers}
            Validator feedback: {validatorReason}
            Mode: {mode}

            Return corrected merged answer in Vietnamese only.
            """;

    @Override
    public String fuse(String originalQuery, List<String> responses, Model model) {
        if (responses == null || responses.isEmpty()) {
            return "Không có thông tin để tổng hợp.";
        }
        if (responses.size() == 1) {
            return responses.get(0);
        }

        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < responses.size(); i++) {
            ctx.append("### RESPONSE ").append(i + 1).append("\n");
            ctx.append(responses.get(i)).append("\n\n");
        }

        PromptTemplate fusionTemplate = new PromptTemplate(FUSION_PROMPT);
        Map<String, Object> fusionVars = Map.of(
                "query", originalQuery,
                "answers", ctx.toString());
        Prompt fusionPrompt = fusionTemplate.create(fusionVars);

        GenerationRequest fusionReq = GenerationRequest.builder()
                .prompt(fusionPrompt)
                .specificModel(model)
                .build();

        log.info("Fusing {} responses for query: {}", responses.size(), originalQuery);
        return generationService.generate(fusionReq);
    }

    @Override
    public String fuseWithCorrection(String originalQuery, List<String> responses, String validatorReason, String mode,
            Model model) {
        if (responses == null || responses.isEmpty()) {
            return "Không có thông tin để tổng hợp.";
        }

        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < responses.size(); i++) {
            ctx.append("### RESPONSE ").append(i + 1).append("\n");
            ctx.append(responses.get(i)).append("\n\n");
        }

        PromptTemplate fusionTemplate = new PromptTemplate(FUSION_SELF_CORRECT_PROMPT);
        Map<String, Object> fusionVars = new HashMap<>();
        fusionVars.put("query", originalQuery);
        fusionVars.put("answers", ctx.toString());
        fusionVars.put("validatorReason", validatorReason != null ? validatorReason : "Không có lý do cụ thể");
        fusionVars.put("mode", mode != null ? mode : "self-correct");
        Prompt fusionPrompt = fusionTemplate.create(fusionVars);

        GenerationRequest fusionReq = GenerationRequest.builder()
                .prompt(fusionPrompt)
                .specificModel(model)
                .build();

        log.info("Fusing with correction (mode: {}) {} responses for query: {}", mode, responses.size(), originalQuery);
        return generationService.generate(fusionReq);
    }
}
