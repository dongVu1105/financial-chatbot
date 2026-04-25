package com.dongVu1105.personal_chatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// Class đại diện cho response nhận về (List các vector)
// Tùy thuộc vào API trả về object hay list trực tiếp mà sửa lại
public record BgeEmbeddingResponse(
        // Ánh xạ key "embeddings" từ JSON vào biến "vectors" của Java
        @JsonProperty("embeddings") List<List<Double>> vectors
) {}