package com.dongVu1105.personal_chatbot.service.RagModules;

import java.util.List;

import com.team14.chatbot.service.RagModules.pipeline.PipelinePlan;
import com.team14.chatbot.service.RagModules.query_processor.QueryProcessingResult;

public interface PlannerService {

    List<PipelinePlan> createPlans(QueryProcessingResult processingResult);

}
