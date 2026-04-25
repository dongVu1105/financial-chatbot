package com.dongVu1105.personal_chatbot.service.RagModules.validator;


import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ValidationResult {
    private boolean isValid;         // Pass hay Fail?

    private String reason;           // Lý do (từ LLM giải thích)

    public static ValidationResult valid() {
        return new ValidationResult(true, "OK");
    }

    public static ValidationResult invalid(String reason) {
        return new ValidationResult(false, reason);
    }
}
