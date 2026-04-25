package com.dongVu1105.personal_chatbot.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "retrieval.filter")
@Data
public class FilterConfig {
    
    /**
     * Enable/disable metadata filtering
     */
    private boolean enabled = true;

    /**
     * Filter rules - key is metadata field name, value is list of allowed values
     * Example:
     * rules:
     *   document_id: ["doc1", "doc2"]
     *   source: ["file1.pdf", "file2.pdf"]
     */
    private Map<String, List<Object>> rules = Map.of();

    /**
     * Filter mode: INCLUDE (only include matching) or EXCLUDE (exclude matching)
     */
    private FilterMode mode = FilterMode.INCLUDE;

    public enum FilterMode {
        INCLUDE,
        EXCLUDE
    }
}

