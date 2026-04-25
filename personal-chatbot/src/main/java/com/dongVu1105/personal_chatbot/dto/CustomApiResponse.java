package com.dongVu1105.personal_chatbot.dto;

import java.util.List;

// Class bao bên ngoài
public record CustomApiResponse(List<Choice> choices) {

    // Helper method để lấy text nhanh
    public String getGeneratedContent() {
        if (choices != null && !choices.isEmpty()) {
            return choices.get(0).message().content();
        }
        return "";
    }

    // Các record con (nested)
    public record Choice(MessageDto message) {}
    public record MessageDto(String role, String content) {}
}