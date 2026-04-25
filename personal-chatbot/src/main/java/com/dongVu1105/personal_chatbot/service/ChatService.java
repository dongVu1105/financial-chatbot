package com.dongVu1105.personal_chatbot.service;

import com.team14.chatbot.repository.HybridChatMemoryRepository;
import com.team14.chatbot.service.SummaryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatService {
    private final ChatClient chatClient;
    // Vector store is injected for potential future use
    private final VectorStore knowledgeBaseStore;
    private final HybridChatMemoryRepository hybridChatMemoryRepository;
    private final RagService ragService;
    private final SummaryService summaryService;

    @Autowired
    public ChatService(
            @Qualifier("knowledgeVectorStore") VectorStore knowledgeBaseStore,
            @Qualifier("geminiFlashClient") ChatClient chatClient,
            HybridChatMemoryRepository hybridChatMemoryRepository,
            RagService ragService,
            SummaryService summaryService) {
        this.knowledgeBaseStore = knowledgeBaseStore;
        this.chatClient = chatClient;
        this.hybridChatMemoryRepository = hybridChatMemoryRepository;
        this.ragService = ragService;
        this.summaryService = summaryService;
    }

    // K_TOKENS is reserved for future token limiting
    @SuppressWarnings("unused")
    private static final int K_TOKENS = 3;

    private static final String PROMPT_TEMPLATE = """
            [PHẦN HƯỚNG DẪN HỆ THỐNG - SYSTEMCONTEXT]
            Bạn là một Trợ lý Tài chính AI chuyên nghiệp.
            Nhiệm vụ của bạn là cung cấp thông tin tài chính chính xác, khách quan và dễ hiểu.

            QUY TẮC TUYỆT ĐỐI:
            1.  **NGÔN NGỮ:** LUÔN LUÔN trả lời bằng tiếng Việt.

            2.  **GIỚI HẠN CHỦ ĐỀ:** Chỉ trả lời các câu hỏi liên quan trực tiếp đến tài chính (ngân hàng, đầu tư, thuế, lãi suất, báo cáo tài chính, v.v.).
                * Nếu người dùng hỏi chủ đề khác (thể thao, giải trí, tình cảm...), hãy từ chối lịch sự.
                * Mẫu câu từ chối: "Xin lỗi, tôi là trợ lý tài chính và chỉ có thể hỗ trợ các câu hỏi liên quan đến lĩnh vực này."

            3.  **CẤM LỜI KHUYÊN ĐẦU TƯ:**
                * TUYỆT ĐỐI KHÔNG khuyến nghị mua/bán cụ thể (ví dụ: KHÔNG nói "Bạn nên mua mã ABC ngay").
                * Chỉ cung cấp dữ liệu, giải thích khái niệm và đưa ra các kịch bản phân tích khách quan.

            4.  **SỬ DỤNG NGỮ CẢNH:**
                * Ưu tiên sử dụng thông tin từ [KNOWLEDGEBASECONTEXT] nếu phù hợp.
                * Tham khảo [CONVERSATIONCONTEXT] để hiểu mạch câu chuyện (ví dụ: "ngân hàng đó" là ngân hàng nào).

            5.  **ĐỊNH DẠNG & TRÌNH BÀY (QUAN TRỌNG ĐỂ TRÁNH LỖI HỆ THỐNG):**
                * **Văn bản:** Sử dụng Markdown chuẩn (In đậm `**từ khóa**`, gạch đầu dòng cho danh sách).
                * **Công thức Toán/Tài chính:**
                    * BẮT BUỘC dùng định dạng LaTeX.
                    * Dùng `$$` bao quanh công thức riêng dòng (Block).
                      Ví dụ: $$ NPV = \\sum \\frac{{CF_t}}{{(1+r)^t}} $$
                    * Dùng `$` bao quanh công thức cùng dòng (Inline). Ví dụ: $ r = 7\\% $
                    * KHÔNG dùng các ký hiệu `\\[`, `\\]`, `\\(`, `\\)`.

            ---
            [PHẦN NGỮ CẢNH TRI THỨC - KNOWLEDGEBASECONTEXT]
            Thông tin tham khảo:
            {KNOWLEDGE_BASE_CONTEXT}

            ---
            [PHẦN TÓM TẮT HỘI THOẠI - SUMMARYCONTEXT]
            {SUMMARY_CONTEXT}

            [PHẦN LỊCH SỬ HỘI THOẠI GẦN ĐÂY - RECENTCONVERSATIONCONTEXT]
            Các tin nhắn gần đây:
            {CONVERSATION_CONTEXT}

            ---
            [PHẦN CÂU HỎI CỦA NGƯỜI DÙNG - USERMESSAGE]
            Dựa vào các quy tắc trên, hãy trả lời câu hỏi:

            """;

    public Message oneTimeResponse(Prompt prompt) {
        return AssistantMessage.builder()
                .content(chatClient.prompt(prompt).call().content())
                .build();
    }

    public Prompt generatePrompt(UserMessage userMessage, String conversationId) {

        List<Document> similarDocuments = new ArrayList<>();

        System.out.println(">>> Similar documents: " + similarDocuments.size());

        String knowledgeBaseContext = similarDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        // Get recent messages for context
        List<Message> messageList = hybridChatMemoryRepository.findByConversationId(conversationId);
        String conversationContext = messageList.isEmpty() ? "" : messageList.toString();
        
        // Get conversation summary
        String summaryContext = "Chưa có tóm tắt hội thoại.";
        try {
            String summary = summaryService.getSummary(conversationId);
            if (summary != null && !summary.trim().isEmpty()) {
                summaryContext = "Tóm tắt hội thoại trước đó: " + summary;
            }
        } catch (Exception e) {
            log.error("Error getting conversation summary", e);
        }

        System.out.println("Conversation context: " + conversationContext);
        System.out.println("Summary context: " + summaryContext);

        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(PROMPT_TEMPLATE);

        Message systemMessage = systemPromptTemplate.createMessage(
                Map.of("KNOWLEDGE_BASE_CONTEXT", knowledgeBaseContext,
                       "CONVERSATION_CONTEXT", conversationContext,
                       "SUMMARY_CONTEXT", summaryContext));

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        // System.out.println(">>> System Message: " + systemMessage.getText());
        System.out.println(">>> User Message: " + userMessage.getText());
        System.out.println(">>> Prompt: " + prompt.getContents());

        return prompt;
    }

    public String generatePrompt_new(UserMessage userMessage, String conversationId) {
        List<Message> messageList = hybridChatMemoryRepository.findByConversationId(conversationId);
        String conversationContext = messageList.isEmpty() ? "" : messageList.toString();
        
        // Include summary in the context for the new prompt
        String summary = summaryService.getSummary(conversationId);
        String fullContext = (summary != null && !summary.isEmpty()) 
            ? summary
            : "";
        log.info("\nconversationId: {}, \n summary: {} \n fullContext: {}", conversationId, summary, fullContext);
            
        return ragService.generate(userMessage.getText(), fullContext);
    }

}
