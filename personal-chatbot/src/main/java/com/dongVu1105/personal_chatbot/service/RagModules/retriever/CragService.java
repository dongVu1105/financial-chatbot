package com.dongVu1105.personal_chatbot.service.RagModules.retriever;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
//@RequiredArgsConstructor
public class CragService {

    private final ChatClient chatClient;

    public CragService(@Qualifier("geminiFlashClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Value("${crag.max-depth:2}")
    private int maxDepth;

    private static final String CRAG_PROMPT_BASE = """
        Bạn là một hệ thống đánh giá chất lượng tài liệu (Document Quality Evaluator) cho hệ thống RAG.
        
        Nhiệm vụ của bạn là đánh giá mức độ liên quan và chất lượng của các tài liệu so với câu hỏi của người dùng.
        
        Bạn sẽ nhận được:
        1. Câu hỏi gốc của người dùng
        2. Danh sách các tài liệu đã được tìm thấy
        
        Hãy đánh giá TỔNG THỂ các tài liệu (không phải từng tài liệu riêng lẻ) và phân loại chúng thành 3 mức:
        
        **GOOD (Tốt)**: 
        - ÍT NHẤT MỘT tài liệu chứa thông tin rõ ràng, trực tiếp trả lời câu hỏi
        - Thông tin chính xác và đầy đủ để trả lời câu hỏi
        - Có thể sử dụng ngay để tạo câu trả lời
        - LƯU Ý: Nếu có bất kỳ tài liệu nào liên quan trực tiếp, hãy đánh giá là GOOD
        
        **AMBIGUOUS (Không chắc chắn / Mơ hồ)**:
        - Tài liệu có vẻ liên quan nhưng không trực tiếp trả lời câu hỏi
        - Chứa thông tin liên quan gián tiếp (ví dụ: câu hỏi về "lạm phát" nhưng tài liệu chỉ nói về "giá vàng")
        - Cần tìm kiếm thêm thông tin để trả lời đầy đủ
        
        **BAD (Xấu)**:
        - TẤT CẢ tài liệu đều hoàn toàn không liên quan đến câu hỏi
        - Không có tài liệu nào chứa thông tin hữu ích
        - CHỈ đánh giá BAD khi chắc chắn không có tài liệu nào liên quan
        
        Hãy trả lời theo định dạng JSON sau (CHỈ JSON, không thêm text):
        {
            "quality": "GOOD",
            "reasoning": "Lý do đánh giá",
            "action": "SEND_TO_GENERATION",
            "newQuery": ""
        }
        
        QUAN TRỌNG: 
        - Chỉ trả lời JSON, không thêm text nào khác
        - Nếu có bất kỳ tài liệu nào liên quan, hãy đánh giá là GOOD
        - Chỉ đánh giá BAD khi chắc chắn không có tài liệu nào liên quan
        """;

    /**
     * Evaluate documents using CRAG (Corrective Retrieval Augmented Generation)
     * @param query The original user query
     * @param documents List of documents to evaluate (typically 3-5 from re-ranking)
     * @return CragEvaluation with quality classification and action
     */
    public CragEvaluation evaluateDocuments(String query, List<Document> documents) {
        if (documents.isEmpty()) {
            return CragEvaluation.builder()
                    .quality(CragEvaluation.DocumentQuality.BAD)
                    .reasoning("Không có tài liệu để đánh giá")
                    .action("SKIP_RAG")
                    .evaluatedDocuments(documents)
                    .build();
        }

        log.info("Evaluating {} documents with CRAG for query: {}", documents.size(), query);

        try {
            // Format documents for prompt
            String documentsText = formatDocumentsForPrompt(documents);

            // Build prompt string directly to avoid template parsing issues
            String promptText = CRAG_PROMPT_BASE + "\n\n" +
                    "Câu hỏi: " + query + "\n\n" +
                    "Tài liệu cần đánh giá:\n" + documentsText;

            // Create prompt without template
            Message systemMessage = new org.springframework.ai.chat.messages.SystemMessage(promptText);
            Message userMessage = new UserMessage("Hãy đánh giá các tài liệu trên.");

            Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

            // Call LLM
            String response = chatClient.prompt(prompt).call().content();
            log.info("CRAG LLM raw response: {}", response);

            // Parse response
            CragEvaluation evaluation = parseCragResponse(response, documents);
            
            log.info("CRAG evaluation: quality={}, action={}, reasoning={}", 
                    evaluation.getQuality(), evaluation.getAction(), evaluation.getReasoning());

            return evaluation;

        } catch (Exception e) {
            log.error("Error in CRAG evaluation", e);
            // Fallback: assume documents are good
            return CragEvaluation.builder()
                    .quality(CragEvaluation.DocumentQuality.GOOD)
                    .reasoning("Lỗi trong quá trình đánh giá, mặc định coi là tốt")
                    .action("SEND_TO_GENERATION")
                    .evaluatedDocuments(documents)
                    .build();
        }
    }

    /**
     * Format documents for prompt
     */
    private String formatDocumentsForPrompt(List<Document> documents) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            sb.append("--- Tài liệu ").append(i + 1).append(" ---\n");
            sb.append("Nội dung: ").append(doc.getText()).append("\n");
            sb.append("Metadata: ").append(doc.getMetadata()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * Parse CRAG LLM response
     */
    private CragEvaluation parseCragResponse(String response, List<Document> documents) {
        try {
            // Try to extract JSON from response
            String json = extractJsonFromResponse(response);
            
            // Simple JSON parsing (for production, use proper JSON parser)
            CragEvaluation.DocumentQuality quality = extractQuality(json);
            String reasoning = extractField(json, "reasoning");
            String action = extractAction(json);
            String newQuery = extractField(json, "newQuery");

            return CragEvaluation.builder()
                    .quality(quality)
                    .reasoning(reasoning)
                    .action(action)
                    .newQuery(newQuery)
                    .evaluatedDocuments(documents)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse CRAG response, using fallback", e);
            // Fallback parsing
            CragEvaluation.DocumentQuality quality = inferQualityFromText(response);
            return CragEvaluation.builder()
                    .quality(quality)
                    .reasoning("Không thể parse response, sử dụng heuristic")
                    .action(mapQualityToAction(quality))
                    .evaluatedDocuments(documents)
                    .build();
        }
    }

    /**
     * Extract JSON from response (handles cases where LLM adds extra text)
     */
    private String extractJsonFromResponse(String response) {
        // Try to find JSON object - match from first { to last }
        Pattern pattern = Pattern.compile("\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            String json = matcher.group();
            log.debug("Extracted JSON from response: {}", json);
            return json;
        }
        
        // Fallback: try simpler pattern
        pattern = Pattern.compile("\\{.*?\\}", Pattern.DOTALL);
        matcher = pattern.matcher(response);
        if (matcher.find()) {
            String json = matcher.group();
            log.debug("Extracted JSON (simple pattern): {}", json);
            return json;
        }
        
        log.warn("Could not extract JSON from response, using full response: {}", response);
        return response;
    }

    /**
     * Extract quality from JSON
     */
    private CragEvaluation.DocumentQuality extractQuality(String json) {
        // Try multiple patterns to catch different JSON formats
        String lowerJson = json.toLowerCase();
        
        // Check for GOOD
        if (json.contains("\"quality\":\"GOOD\"") || 
            json.contains("'quality':'GOOD'") ||
            json.contains("\"quality\" : \"GOOD\"") ||
            json.contains("\"quality\": \"GOOD\"") ||
            lowerJson.contains("\"quality\":\"good\"") ||
            lowerJson.contains("quality: good") ||
            lowerJson.contains("\"quality\": good")) {
            log.debug("Extracted quality: GOOD");
            return CragEvaluation.DocumentQuality.GOOD;
        } 
        
        // Check for AMBIGUOUS
        if (json.contains("\"quality\":\"AMBIGUOUS\"") || 
            json.contains("'quality':'AMBIGUOUS'") ||
            json.contains("\"quality\" : \"AMBIGUOUS\"") ||
            json.contains("\"quality\": \"AMBIGUOUS\"") ||
            lowerJson.contains("\"quality\":\"ambiguous\"") ||
            lowerJson.contains("quality: ambiguous") ||
            lowerJson.contains("\"quality\": ambiguous")) {
            log.debug("Extracted quality: AMBIGUOUS");
            return CragEvaluation.DocumentQuality.AMBIGUOUS;
        } 
        
        // Check for BAD
        if (json.contains("\"quality\":\"BAD\"") || 
            json.contains("'quality':'BAD'") ||
            json.contains("\"quality\" : \"BAD\"") ||
            json.contains("\"quality\": \"BAD\"") ||
            lowerJson.contains("\"quality\":\"bad\"") ||
            lowerJson.contains("quality: bad") ||
            lowerJson.contains("\"quality\": bad")) {
            log.debug("Extracted quality: BAD");
            return CragEvaluation.DocumentQuality.BAD;
        }
        
        // If no match found, log warning and try fallback
        log.warn("Could not extract quality from JSON, trying fallback. JSON: {}", json);
        return inferQualityFromText(json);
    }

    /**
     * Extract field value from JSON
     */
    private String extractField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * Extract action from JSON
     */
    private String extractAction(String json) {
        // Try to extract using regex pattern (more reliable)
        String action = extractField(json, "action");
        if (!action.isEmpty()) {
            String upperAction = action.toUpperCase();
            if (upperAction.contains("SEND_TO_GENERATION") || upperAction.equals("SEND_TO_GENERATION")) {
                log.debug("Extracted action: SEND_TO_GENERATION");
                return "SEND_TO_GENERATION";
            } else if (upperAction.contains("ACTIVE_RETRIEVAL") || upperAction.equals("ACTIVE_RETRIEVAL")) {
                log.debug("Extracted action: ACTIVE_RETRIEVAL");
                return "ACTIVE_RETRIEVAL";
            } else if (upperAction.contains("SKIP_RAG") || upperAction.equals("SKIP_RAG")) {
                log.debug("Extracted action: SKIP_RAG");
                return "SKIP_RAG";
            }
        }
        
        // Fallback: try contains() method
        String lowerJson = json.toLowerCase();
        
        // Check for SEND_TO_GENERATION with various formats
        if (json.contains("\"action\":\"SEND_TO_GENERATION\"") || 
            json.contains("'action':'SEND_TO_GENERATION'") ||
            json.contains("\"action\" : \"SEND_TO_GENERATION\"") ||
            json.contains("\"action\": \"SEND_TO_GENERATION\"") ||
            lowerJson.contains("\"action\":\"send_to_generation\"") ||
            lowerJson.contains("action: send_to_generation")) {
            log.debug("Extracted action: SEND_TO_GENERATION (fallback)");
            return "SEND_TO_GENERATION";
        } 
        
        // Check for ACTIVE_RETRIEVAL with various formats
        if (json.contains("\"action\":\"ACTIVE_RETRIEVAL\"") || 
            json.contains("'action':'ACTIVE_RETRIEVAL'") ||
            json.contains("\"action\" : \"ACTIVE_RETRIEVAL\"") ||
            json.contains("\"action\": \"ACTIVE_RETRIEVAL\"") ||
            lowerJson.contains("\"action\":\"active_retrieval\"") ||
            lowerJson.contains("action: active_retrieval")) {
            log.debug("Extracted action: ACTIVE_RETRIEVAL (fallback)");
            return "ACTIVE_RETRIEVAL";
        } 
        
        // Check for SKIP_RAG with various formats
        if (json.contains("\"action\":\"SKIP_RAG\"") || 
            json.contains("'action':'SKIP_RAG'") ||
            json.contains("\"action\" : \"SKIP_RAG\"") ||
            json.contains("\"action\": \"SKIP_RAG\"") ||
            lowerJson.contains("\"action\":\"skip_rag\"") ||
            lowerJson.contains("action: skip_rag")) {
            log.debug("Extracted action: SKIP_RAG (fallback)");
            return "SKIP_RAG";
        }
        
        // Default: safer to send to generation than skip
        log.warn("Could not extract action from JSON, defaulting to SEND_TO_GENERATION. JSON: {}", json);
        return "SEND_TO_GENERATION";
    }

    /**
     * Infer quality from text (fallback)
     */
    private CragEvaluation.DocumentQuality inferQualityFromText(String text) {
        String lowerText = text.toLowerCase();
        
        // Check for GOOD indicators
        if (lowerText.contains("\"good\"") || 
            lowerText.contains("'good'") ||
            lowerText.contains("quality: good") ||
            lowerText.contains("quality\":\"good") ||
            (lowerText.contains("good") && (lowerText.contains("quality") || lowerText.contains("đánh giá")))) {
            log.debug("Inferred quality as GOOD from text");
            return CragEvaluation.DocumentQuality.GOOD;
        } 
        
        // Check for AMBIGUOUS indicators
        if (lowerText.contains("\"ambiguous\"") || 
            lowerText.contains("'ambiguous'") ||
            lowerText.contains("quality: ambiguous") ||
            lowerText.contains("quality\":\"ambiguous") ||
            lowerText.contains("mơ hồ") || 
            lowerText.contains("không chắc")) {
            log.debug("Inferred quality as AMBIGUOUS from text");
            return CragEvaluation.DocumentQuality.AMBIGUOUS;
        } 
        
        // Check for BAD indicators
        if (lowerText.contains("\"bad\"") || 
            lowerText.contains("'bad'") ||
            lowerText.contains("quality: bad") ||
            lowerText.contains("quality\":\"bad") ||
            (lowerText.contains("bad") && (lowerText.contains("quality") || lowerText.contains("đánh giá")))) {
            log.debug("Inferred quality as BAD from text");
            return CragEvaluation.DocumentQuality.BAD;
        }
        
        // Default: if contains "tốt" or "liên quan", assume GOOD
        if (lowerText.contains("tốt") || lowerText.contains("liên quan") || lowerText.contains("trả lời")) {
            log.debug("Inferred quality as GOOD from Vietnamese keywords");
            return CragEvaluation.DocumentQuality.GOOD;
        }
        
        // Last resort: default to GOOD (safer than BAD)
        log.warn("Could not infer quality from text, defaulting to GOOD. Text: {}", text);
        return CragEvaluation.DocumentQuality.GOOD;
    }

    /**
     * Map quality to action
     */
    private String mapQualityToAction(CragEvaluation.DocumentQuality quality) {
        return switch (quality) {
            case GOOD -> "SEND_TO_GENERATION";
            case AMBIGUOUS -> "ACTIVE_RETRIEVAL";
            case BAD -> "SKIP_RAG";
        };
    }
}

