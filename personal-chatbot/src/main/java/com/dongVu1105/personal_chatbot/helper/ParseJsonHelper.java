package com.dongVu1105.personal_chatbot.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ParseJsonHelper {

    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parse JSON response from LLM
     */
    public <T> T parseJsonResponse(String rawResponse, Class<T> responseType) throws Exception {
        // Extract JSON from response (in case LLM adds markdown or extra text)
        String jsonContent = extractJson(rawResponse);

        // Parse using Jackson
        return objectMapper.readValue(jsonContent, responseType);
    }

    /**
     * Extract JSON content from response (removes markdown code blocks if present)
     */
    private String extractJson(String response) {
        // Try to find JSON in markdown code block
        Pattern jsonBlockPattern = Pattern.compile("```json\\s*\\n(.+?)\\n```", Pattern.DOTALL);
        Matcher matcher = jsonBlockPattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // Try to find JSON in generic code block
        Pattern codeBlockPattern = Pattern.compile("```\\s*\\n(.+?)\\n```", Pattern.DOTALL);
        matcher = codeBlockPattern.matcher(response);
        if (matcher.find()) {
            String content = matcher.group(1).trim();
            if (content.startsWith("{") || content.startsWith("[")) {
                return content;
            }
        }

        // Try to find JSON object or array in the text
        Pattern jsonPattern = Pattern.compile("(\\{.+?\\}|\\[.+?\\])", Pattern.DOTALL);
        matcher = jsonPattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // If no JSON markers found, assume entire response is JSON
        return response.trim();
    }
}
