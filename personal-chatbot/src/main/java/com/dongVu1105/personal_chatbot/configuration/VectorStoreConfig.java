package com.dongVu1105.personal_chatbot.configuration;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Primary;

@Configuration
public class VectorStoreConfig {

    @Bean(name = "knowledgeVectorStore")
//    @Primary
    @Qualifier("knowledgeVectorStore")
    public VectorStore knowledgeBaseVectorStore(JdbcTemplate jdbcTemplate,
//                                                @Qualifier("googleGenAiTextEmbedding") EmbeddingModel embeddingModel
    BgeM3EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel) // Builder có tham số
                .vectorTableName("knowledge_embedding_view") // <-- BẮT BUỘC PHẢI THÊM
                .dimensions(1024)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(false) // Tự động tạo table nếu chưa có
                .build();
    }

    @Bean(name = "caseStudiesVectorStore")
//    @Primary
    @Qualifier("caseStudiesVectorStore")
    public VectorStore caseStudiesVectorStore(JdbcTemplate jdbcTemplate,
//                                                @Qualifier("googleGenAiTextEmbedding") EmbeddingModel embeddingModel
                                                BgeM3EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel) // Builder có tham số
                .vectorTableName("case_studies_embedding_view") // <-- BẮT BUỘC PHẢI THÊM
                .dimensions(1024)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(false) // Tự động tạo table nếu chưa có
                .build();
    }

    @Bean(name = "chatMemoryVectorStore")
    @Qualifier("chatMemoryVectorStore")
    public VectorStore chatMemoryVectorStore(JdbcTemplate jdbcTemplate,
//                                             @Qualifier("googleGenAiTextEmbedding") EmbeddingModel embeddingModel
    BgeM3EmbeddingModel embeddingModel
    ) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel) // Builder có tham số
                .vectorTableName("chat_memory_embeddings") // <-- BẮT BUỘC PHẢI THÊM
                .dimensions(1024)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
    }
}