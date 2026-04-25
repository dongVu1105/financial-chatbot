package com.dongVu1105.personal_chatbot.service.RagModules.calculator;

import com.team14.chatbot.service.RagModules.CalculatorService;
import com.udojava.evalex.Expression;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class CalculationServiceImpl implements CalculatorService {

    @Override
    public CalculationResult calculate(String expr) {
        log.info("Expression: {}", expr);
        try {
            Expression expression = new Expression(expr);
            BigDecimal result = expression.eval();

            log.info("Result: {}", result);

            return CalculationResult.builder()
                    .isSuccess(true)
                    .value(result)
                    .expression(expr)
                    .build();

        } catch (Exception e) {
            log.error("Error evaluating expression: {}", expr, e);
            return CalculationResult.builder()
                    .isSuccess(false)
                    .value(null)
                    .expression(expr)
                    .build();
        }

    }

}
