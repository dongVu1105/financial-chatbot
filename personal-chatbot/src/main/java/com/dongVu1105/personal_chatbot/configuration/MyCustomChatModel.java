package com.dongVu1105.personal_chatbot.configuration;

import com.team14.chatbot.dto.CustomApiRequest;
import com.team14.chatbot.dto.CustomApiResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

public class MyCustomChatModel implements ChatModel {

    private final RestClient restClient;
    private final String apiUrl;
    private final ChatOptions defaultOptions;

    public MyCustomChatModel(RestClient.Builder restClientBuilder, String apiUrl, ChatOptions defaultOptions) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(30)); // Timeout 10 giây
        requestFactory.setReadTimeout(Duration.ofSeconds(180));

        this.restClient = restClientBuilder
                .requestFactory(requestFactory) // Ép RestClient dùng factory này
                .build();
        this.apiUrl = apiUrl;
        this.defaultOptions = defaultOptions;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        // 1. Chuyển đổi Prompt (Spring AI) thành Request Body (API của bạn)
        CustomApiRequest request = toCustomRequest(prompt);

        // 2. Gọi API bên ngoài
        CustomApiResponse apiResponse = restClient.post()
                .uri(apiUrl)
                .body(request)
                .retrieve()
                .body(CustomApiResponse.class);

        if (apiResponse == null) {
            throw new RuntimeException("API trả về null");
        }

        // 3. Chuyển đổi Response (API của bạn) thành ChatResponse (Spring AI)
        return toChatResponse(apiResponse);
    }

    // --- Logic Mapping ---

    private CustomApiRequest toCustomRequest(Prompt prompt) {
        // Lấy nội dung tin nhắn user.
        // Lưu ý: API đơn giản thường chỉ lấy text, API phức tạp cần loop qua List<Message>
        String combinedContent = prompt.getInstructions().stream()
                .map(msg -> msg.getText())
                .collect(Collectors.joining("\n"));

        Double temp = (this.defaultOptions != null) ? this.defaultOptions.getTemperature() : 0.7;

        // Merge options từ prompt nếu có
        if (prompt.getOptions() != null && prompt.getOptions().getTemperature() != null) {
            temp = prompt.getOptions().getTemperature();
        }

        return new CustomApiRequest(combinedContent, temp);
    }

    private ChatResponse toChatResponse(CustomApiResponse apiResponse) {
        // SỬA: Dùng hàm getGeneratedContent() mới tạo
        String content = apiResponse.getGeneratedContent();

        AssistantMessage assistantMessage = new AssistantMessage(content);

        // Tạo Metadata (tùy chọn)
        ChatGenerationMetadata metadata = ChatGenerationMetadata.NULL;

        // Tạo Generation
        Generation generation = new Generation(assistantMessage, metadata);

        // Trả về ChatResponse chứa danh sách generations
        return new ChatResponse(List.of(new Generation(assistantMessage, ChatGenerationMetadata.NULL)));    }

    // --- Các phương thức khác của Interface ---

    @Override
    public ChatOptions getDefaultOptions() {
        return this.defaultOptions;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // Nếu API của bạn hỗ trợ SSE (Server Sent Events), bạn cần dùng WebClient để stream.
        // Ở đây mình ném lỗi chưa hỗ trợ cho đơn giản.
        throw new UnsupportedOperationException("Streaming chưa được hỗ trợ trong demo này");
    }
}