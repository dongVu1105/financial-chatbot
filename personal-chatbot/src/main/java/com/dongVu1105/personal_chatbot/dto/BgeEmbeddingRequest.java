package com.dongVu1105.personal_chatbot.dto;

import java.util.List;

// Class đại diện cho request gửi đi
public record BgeEmbeddingRequest(List<String> texts) {}