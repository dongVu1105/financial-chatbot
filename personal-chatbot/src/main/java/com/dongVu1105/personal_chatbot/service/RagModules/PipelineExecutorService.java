package com.dongVu1105.personal_chatbot.service.RagModules;

import com.team14.chatbot.service.RagModules.pipeline.PipelinePlan;

public interface PipelineExecutorService {

    String execute(PipelinePlan plan);

}
