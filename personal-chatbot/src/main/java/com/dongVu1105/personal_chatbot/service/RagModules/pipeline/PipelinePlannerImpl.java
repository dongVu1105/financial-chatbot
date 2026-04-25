package com.dongVu1105.personal_chatbot.service.RagModules.pipeline;

import com.team14.chatbot.enums.QueryIntent;
import com.team14.chatbot.service.RagModules.PlannerService;
import com.team14.chatbot.service.RagModules.generation.Model;
import com.team14.chatbot.service.RagModules.query_processor.IntentTask;
import com.team14.chatbot.service.RagModules.query_processor.QueryProcessingResult;

import com.team14.chatbot.service.RagModules.retriever.RetrievalType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PipelinePlannerImpl implements PlannerService {

  @Override
  public List<PipelinePlan> createPlans(QueryProcessingResult processingResult) {
    List<IntentTask> tasks = processingResult.intents();
    return tasks
        .stream()
        .map(task -> createPlan(task.intent(), task.query(), processingResult))
        .toList();
  }

  private PipelinePlan createPlan(QueryIntent intent, String userQuery, QueryProcessingResult processingResult) {
    // Chọn pipelineQuery & hydeDocument từ kết quả processing (fallback về query
    // gốc)
    String pipelineQuery = processingResult.stepBackQuestion() != null
        && !processingResult.stepBackQuestion().isBlank()
            ? processingResult.stepBackQuestion().trim()
            : userQuery;
    String hydeDocument = processingResult.hydeDocument();

    try {
      return switch (intent) {
        case KNOWLEDGE_QUERY -> buildKnowledgePlan(intent, userQuery, pipelineQuery, hydeDocument);
        case ADVISORY -> buildAdvisoryPlan(intent, userQuery, pipelineQuery, hydeDocument);
        case CALCULATION -> buildCalculationPlan(intent, userQuery, pipelineQuery, hydeDocument);
        case MALICIOUS_CONTENT -> {
          yield PipelinePlan.builder()
              .intent(intent.name())
              .query(userQuery)
              .directResponse("Phát hiện nội dung độc hại, vui lòng đặt lại câu hỏi khác.")
              .build();
        }
        case NON_FINANCIAL -> buildDirectPlan(intent, userQuery);
        default -> buildDirectPlan(intent, userQuery);
      };
    } catch (Exception e) {
      log.error("Planner error", e);
      return PipelinePlan.builder()
          .intent("ERROR")
          .query(userQuery)
          .directResponse("Xin lỗi, hệ thống đang bận. Vui lòng thử lại sau.")
          .build();
    }
  }

  private PipelinePlan buildDirectPlan(QueryIntent intent, String userQuery) {
    return PipelinePlan.builder()
        .intent(intent.name())
        .query(userQuery)
        .pipelineQuery(userQuery)
        .hydeDocument(null)
        .queryProcessingConfig(null)
        .retrievalConfig(null)
        .calculationConfig(null)
            .generationConfig(PipelinePlan.GenerationConfig.builder()
                    .model(Model.GEMINI_2_5_FLASH_LITE)
                    .build())
        .build();
  }

  private PipelinePlan buildKnowledgePlan(QueryIntent intent, String userQuery,
      String pipelineQuery, String hydeDocument) {
    return PipelinePlan.builder()
        .intent(intent.name())
        .query(userQuery)
        .pipelineQuery(pipelineQuery)
        .hydeDocument(hydeDocument)
        .queryProcessingConfig(
            PipelinePlan.QueryProcessingConfig.builder()
                .enableStepBack(true)
                .enableHyde(true)
                .enableMultiQuery(false)
                .multiQueryCount(0)
                .build())
        // For knowledge queries: keep pipeline simple -> 1 retrieve, 1 gen, 1 validate

        .retrievalConfig(PipelinePlan.RetrievalConfig.builder()
            .query(pipelineQuery)
            .topK(5)
            .retrievalType(RetrievalType.KNOWLEDGE_RETRIEVE)
            .build())
        .calculationConfig(null)
        .generationConfig(PipelinePlan.GenerationConfig.builder()
            .model(Model.GEMINI_2_5_FLASH)
            .build())
        .build();
  }

  private PipelinePlan buildAdvisoryPlan(QueryIntent intent, String userQuery,
      String pipelineQuery, String hydeDocument) {
    return PipelinePlan.builder()
        .intent(intent.name())
        .query(userQuery)
        .pipelineQuery(pipelineQuery)
        .hydeDocument(hydeDocument)
        // Enable all advanced techniques for advisory queries
        .queryProcessingConfig(
            PipelinePlan.QueryProcessingConfig.builder()
                .enableStepBack(true)
                .enableHyde(false)
                .enableMultiQuery(false)
                .multiQueryCount(0)
                .build())
        .retrievalConfig(null
        // PipelinePlan.RetrievalConfig.builder()
        // .query(userQuery)
        // .topK(5)
        // .retrievalType(RetrievalType.CASE_STUDIES_RETRIEVE)
        // .build()
        )
        .calculationConfig(null)
        .generationConfig(PipelinePlan.GenerationConfig.builder()
            .model(Model.GEMINI_2_5_FLASH)
            .build())
        .build();
  }

  private PipelinePlan buildCalculationPlan(QueryIntent intent, String userQuery,
      String pipelineQuery, String hydeDocument) {
    return PipelinePlan.builder()
        .intent(intent.name())
        .query(userQuery)
        .pipelineQuery(pipelineQuery)
        .hydeDocument(hydeDocument)
        .queryProcessingConfig(null
        // PipelinePlan.QueryProcessingConfig.builder()
        // .enableStepBack(true)
        // .enableHyde(true)
        // .enableMultiQuery(true)
        // .multiQueryCount(3)
        // .build()
        )
        .calculationConfig(
            PipelinePlan.CalculationConfig.builder()
                .isCalculationNeeded(true)
                .build())
        .generationConfig(PipelinePlan.GenerationConfig.builder()
            .model(Model.GEMINI_2_5_FLASH)
            .build())
        .build();
  }
}
