package com.dongVu1105.personal_chatbot.service.RagModules.pipeline;

import com.team14.chatbot.service.RagModules.generation.Model;
import com.team14.chatbot.service.RagModules.retriever.RetrievalType;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder
@Getter
@ToString
public class PipelinePlan {

    // 1. Cấu hình Intent (để log hoặc direct response)
    private String intent;
    private String directResponse; // Nếu có cái này thì Executor trả về luôn

    // Query gốc của người dùng
    private String query;

    // Query đã được xử lý (ví dụ: step-back) dùng cho pipeline executor
    private String pipelineQuery;

    // Tài liệu HyDE tương ứng với query (nếu có)
    private String hydeDocument;

    // 2. Cấu hình các Module (Nullable - Null nghĩa là không chạy)
    private QueryProcessingConfig queryProcessingConfig;

    // Cấu hình tìm kiếm
    private RetrievalConfig retrievalConfig;

    // Cấu hình tính toán
    private CalculationConfig calculationConfig;

    // Cấu hình sinh câu trả lời (Hầu như lúc nào cũng có)
    private GenerationConfig generationConfig;

    // --- Inner Config Classes ---
    @Builder
    @Getter
    public static class QueryProcessingConfig {
        private boolean enableMultiQuery;
        private int multiQueryCount;
        private boolean enableStepBack;
        private boolean enableHyde;
    }



    @Builder
    @Getter
    public static class RetrievalConfig {
        private RetrievalType retrievalType;
        private String query;
        private int topK;
    }

    @Builder
    @Getter
    public static class CalculationConfig {
        private boolean isCalculationNeeded;
//        private String expression;
//        private Map<String, Object> variables;
    }

    @Builder
    @Getter
    public static class GenerationConfig {
        private Model model;
        private Double temperature;
    }
}