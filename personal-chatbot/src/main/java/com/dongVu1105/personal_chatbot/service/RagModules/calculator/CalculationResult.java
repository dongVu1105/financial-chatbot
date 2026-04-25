package com.dongVu1105.personal_chatbot.service.RagModules.calculator;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CalculationResult {
    private boolean isSuccess;
    private String expression;
    private BigDecimal value;
}
