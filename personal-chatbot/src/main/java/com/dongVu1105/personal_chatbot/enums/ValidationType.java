package com.dongVu1105.personal_chatbot.enums;

public enum ValidationType {
    // Input Checks
    INPUT_SAFETY,       // Kiểm tra độc hại, chính trị, bạo lực...
    PROMPT_INJECTION,   // Kiểm tra nỗ lực hack prompt của hệ thống

    // Output Checks
    OUTPUT_SAFETY,      // Kiểm tra câu trả lời có an toàn không
    HALLUCINATION_CHECK // (Quan trọng) Kiểm tra câu trả lời có bịa đặt so với Context không

}
