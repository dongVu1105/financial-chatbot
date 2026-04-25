package com.dongVu1105.personal_chatbot.configuration;

import com.team14.chatbot.dto.BgeEmbeddingRequest;
import com.team14.chatbot.dto.BgeEmbeddingResponse;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
@Primary
public class BgeM3EmbeddingModel implements EmbeddingModel {

    private final RestClient restClient;
    // URL từ ngrok của bạn
    private final String API_URL = "https://hypergamous-bernadine-unspitefully.ngrok-free.dev/embed_batch";

    public BgeM3EmbeddingModel(RestClient.Builder restClientBuilder) {
        // --- PHẦN QUAN TRỌNG NHẤT: SỬA LỖI DNS ---
        // Tạo factory cơ bản của Java (dùng OS DNS thay vì Netty DNS)
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000); // Timeout 10 giây
        requestFactory.setReadTimeout(10_000);

        this.restClient = restClientBuilder
                .requestFactory(requestFactory) // Ép RestClient dùng factory này
                .build();
        // -----------------------------------------
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        // 1. Lấy danh sách text từ request của Spring AI
        List<String> inputs = request.getInstructions();
        

        // 2. Gọi API custom
        // Lưu ý: Cần đảm bảo cấu trúc body khớp với API Python của bạn
        List<List<Double>> vectors = restClient.post()
                .uri(API_URL)
                .body(new BgeEmbeddingRequest(inputs))
                .retrieve()
                .body(BgeEmbeddingResponse.class) // Hoặc List.class nếu API trả về mảng trực tiếp
                .vectors();

        // 3. Map kết quả trả về format của Spring AI

        // Map kết quả trả về format của Spring AI
        List<Embedding> embeddings = new ArrayList<>();

        for (int i = 0; i < vectors.size(); i++) {
            // 1. Lấy vector dạng List<Double> từ response
            List<Double> doubleVector = vectors.get(i);

            // 2. Tạo mảng float[] có kích thước tương ứng
            float[] floatVector = new float[doubleVector.size()];

            // 3. Chuyển đổi từng phần tử từ Double sang float
            for (int j = 0; j < doubleVector.size(); j++) {
                floatVector[j] = doubleVector.get(j).floatValue();
            }

            // 4. Tạo đối tượng Embedding với mảng float[]
            embeddings.add(new Embedding(floatVector, i));
        }

        // 4. Trả về EmbeddingResponse (metadata có thể để null hoặc thêm nếu cần)
        return new EmbeddingResponse(embeddings);
    }

    // Các method overload bắt buộc khác
    @Override
    public float[] embed(String text) {
        EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);

        // Lấy kết quả embedding (đang là float[])
        return this.call(request).getResults().get(0).getOutput();
    }

    @Override
    public float[] embed(org.springframework.ai.document.Document document) {
        return embed(document.getFormattedContent(MetadataMode.NONE));
    }
}