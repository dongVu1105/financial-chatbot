package com.dongVu1105.personal_chatbot.service;

import com.team14.chatbot.service.RagModules.generation.Model;
import com.team14.chatbot.service.RagModules.pipeline.PipelinePlan;
import com.team14.chatbot.enums.QueryIntent;
import com.team14.chatbot.service.RagModules.FusionService;
import com.team14.chatbot.service.RagModules.PipelineExecutorService;
import com.team14.chatbot.service.RagModules.PlannerService;
import com.team14.chatbot.service.RagModules.ValidatorService;
import com.team14.chatbot.service.RagModules.query_processor.QueryProcessingService;
import com.team14.chatbot.service.RagModules.query_processor.IntentTask;
import com.team14.chatbot.service.RagModules.query_processor.QueryProcessingResult;
import com.team14.chatbot.service.RagModules.validator.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private final PipelineExecutorService pipelineExecutor;
    private final QueryProcessingService queryProcessor;
    private final PlannerService planner;
    private final FusionService fusionService;
    private final ValidatorService validatorService;

    public String generate(String userQuery, String conversationHistory) {
        log.info(">>> NEW REQUEST: {}", userQuery);

        // B0: validate input
        ValidationResult inputValidation = validatorService.validateInput(userQuery);
        if (!inputValidation.isValid()) {
            return "Yêu cầu không hợp lệ: " + inputValidation.getReason();
        }
        log.info("Yêu cầu hợp lệ!");

        // B1: xử lý query tổng hợp (intent + step-back + HyDE)
        QueryProcessingResult processingResult = queryProcessor.execute(userQuery, conversationHistory);
        List<IntentTask> tasks = processingResult.intents();

        if (tasks.stream().anyMatch(intentTask -> intentTask.intent() == QueryIntent.MALICIOUS_CONTENT)) {
            return "Phát hiện nội dung độc hại, vui lòng đặt lại câu hỏi khác.";
        }

        log.info("Intents: {}", tasks.stream().map(IntentTask::intent).toList());
        log.info("StepBack question: {}", processingResult.stepBackQuestion());
        log.info("HyDE document length: {}", processingResult.hydeDocument() != null
                ? processingResult.hydeDocument().length()
                : 0);

        // B3: tạo plan cho intent (hiện tại 1 intent; có thể mở rộng multi-intent sau)
        List<PipelinePlan> plans = planner.createPlans(processingResult);
        log.info("Plans: {}", plans.stream().map(PipelinePlan::toString));

        // B4: thực thi song song từng intent pipeline
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = plans.stream()
                    .map(p -> executor.submit(() -> pipelineExecutor.execute(p)))
                    .toList();
            List<String> responses = new ArrayList<>();
            for (Future<String> f : futures) {
                try {
                    String r = f.get();
                    if (r != null && !r.isBlank()) {
                        responses.add(r);
                    }
                } catch (Exception e) {
                    log.error("Intent pipeline failed", e);
                }
            }

            if (responses.isEmpty())
                return "Không có phản hồi từ các pipeline.";
            String fusedAns = responses.size() == 1
                    ? responses.get(0)
                    : fusionService.fuse(userQuery, responses, Model.GEMINI_2_5_FLASH);
            return validateAndRecover(fusedAns, userQuery, responses, tasks.stream().map(IntentTask::intent).toList());
        } catch (Exception e) {
            log.error("Pipeline execution failed", e);
            return "Xin lỗi, hệ thống gặp lỗi khi xử lý yêu cầu.";
        }
    }

    /**
     * Validate fused output; if fail, self-correct once, then retry regenerate
     * twice, else fallback.
     */
    private String validateAndRecover(String fused, String originalQuery, List<String> parts,
            List<QueryIntent> intents) {
        boolean hasCoreIntent = intents.stream().anyMatch(intent -> intent == QueryIntent.KNOWLEDGE_QUERY
                || intent == QueryIntent.ADVISORY
                || intent == QueryIntent.CALCULATION);

        if (!hasCoreIntent) {
            return fused;
        }

        Map<String, String> emptyContexts = new HashMap<>();
        ValidationResult vr = validatorService.validateOutput(fused, originalQuery, emptyContexts);
        if (vr.isValid())
            return fused;

        // Self-correct once
        String candidate = regenerateFusion(originalQuery, parts, "self-correct", vr.getReason());
        vr = validatorService.validateOutput(candidate, originalQuery, emptyContexts);
        if (vr.isValid())
            return candidate;

        // Retry regenerate up to 2 times
        for (int i = 1; i <= 2; i++) {
            candidate = regenerateFusion(originalQuery, parts, "retry_" + i, vr.getReason());
            vr = validatorService.validateOutput(candidate, originalQuery, emptyContexts);
            if (vr.isValid())
                return candidate;
        }

        return candidate;
    }

    private String regenerateFusion(String originalQuery, List<String> parts, String mode, String validatorReason) {
        return fusionService.fuseWithCorrection(originalQuery, parts, validatorReason, mode, Model.GEMINI_2_5_FLASH);
    }

}
