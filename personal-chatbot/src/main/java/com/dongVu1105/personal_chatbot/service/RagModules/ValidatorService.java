package com.dongVu1105.personal_chatbot.service.RagModules;

import com.team14.chatbot.service.RagModules.validator.ValidationResult;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

public interface ValidatorService {

    ValidationResult validateInput(String rawQuery);

    ValidationResult validateOutput(String generatedResponse, String userQuery, Map<String, String> contexts);
}