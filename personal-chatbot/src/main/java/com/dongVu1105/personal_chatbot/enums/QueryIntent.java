package com.dongVu1105.personal_chatbot.enums;

/**
 * Enum representing the different intents that can be classified from user
 * queries.
 * Used by the Query Router to determine the appropriate processing pipeline.
 */
public enum QueryIntent {
    KNOWLEDGE_QUERY("Tìm kiếm kiến thức (VD: \"Lãi suất là gì?\")"),
    ADVISORY("Xin lời khuyên (VD: \"Nên mua vàng hay Đô?\")"),
    CALCULATION("Tính toán (VD: \"Tôi muốn tính toán lãi suất ngân hàng\")"),
    UNSUPPORTED("Không thuộc 3 intent trên"),
    MALICIOUS_CONTENT("Nội dung độc hại / không phù hợp"),
    NON_FINANCIAL("Không liên quan đến lĩnh vực tài chính");

    private final String description;

    QueryIntent(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
