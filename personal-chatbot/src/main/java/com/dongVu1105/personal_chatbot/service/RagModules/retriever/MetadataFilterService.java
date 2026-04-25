package com.dongVu1105.personal_chatbot.service.RagModules.retriever;

import com.team14.chatbot.configuration.FilterConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetadataFilterService {

    private final FilterConfig filterConfig;

    /**
     * Apply metadata filters to documents
     * @param documents List of documents to filter
     * @param customFilters Optional custom filters to apply (overrides config)
     * @return Filtered list of documents
     */
    public List<Document> filterDocuments(List<Document> documents, Map<String, Object> customFilters) {
        if (!filterConfig.isEnabled() && (customFilters == null || customFilters.isEmpty())) {
            log.debug("Metadata filtering is disabled, returning all documents");
            return documents;
        }

        // Use custom filters if provided, otherwise use config rules
        Map<String, List<Object>> rulesToApply = customFilters != null && !customFilters.isEmpty()
                ? convertToRules(customFilters)
                : filterConfig.getRules();

        if (rulesToApply.isEmpty()) {
            return documents;
        }

        log.debug("Applying metadata filters: {} rules, mode: {}", 
                rulesToApply.size(), filterConfig.getMode());

        return documents.stream()
                .filter(doc -> matchesFilter(doc, rulesToApply))
                .collect(Collectors.toList());
    }

    /**
     * Check if document matches filter rules
     */
    private boolean matchesFilter(Document document, Map<String, List<Object>> rules) {
        Map<String, Object> metadata = document.getMetadata();
        boolean matches = true;

        for (Map.Entry<String, List<Object>> rule : rules.entrySet()) {
            String field = rule.getKey();
            List<Object> allowedValues = rule.getValue();

            Object fieldValue = metadata.get(field);
            boolean fieldMatches = fieldValue != null && allowedValues.contains(fieldValue);

            if (!fieldMatches) {
                matches = false;
                break;
            }
        }

        // Apply filter mode
        if (filterConfig.getMode() == FilterConfig.FilterMode.EXCLUDE) {
            return !matches; // Exclude matching documents
        } else {
            return matches; // Include matching documents
        }
    }

    /**
     * Convert custom filter map to rules format
     * Example: {"document_id": "doc1"} -> {"document_id": ["doc1"]}
     */
    private Map<String, List<Object>> convertToRules(Map<String, Object> customFilters) {
        return customFilters.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            Object value = entry.getValue();
                            if (value instanceof List) {
                                return (List<Object>) value;
                            } else {
                                return List.of(value);
                            }
                        }
                ));
    }

    /**
     * Filter documents by specific metadata field and value
     */
    public List<Document> filterByField(List<Document> documents, String field, Object value) {
        return documents.stream()
                .filter(doc -> {
                    Object fieldValue = doc.getMetadata().get(field);
                    return value.equals(fieldValue);
                })
                .collect(Collectors.toList());
    }

    /**
     * Filter documents by multiple metadata fields (AND condition)
     */
    public List<Document> filterByFields(List<Document> documents, Map<String, Object> filters) {
        return documents.stream()
                .filter(doc -> {
                    Map<String, Object> metadata = doc.getMetadata();
                    return filters.entrySet().stream()
                            .allMatch(entry -> {
                                Object fieldValue = metadata.get(entry.getKey());
                                return entry.getValue().equals(fieldValue);
                            });
                })
                .collect(Collectors.toList());
    }
}

