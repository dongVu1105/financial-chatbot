package com.dongVu1105.personal_chatbot.service.RagModules;


import com.team14.chatbot.service.RagModules.calculator.CalculationResult;

public interface CalculatorService {
    /**
     * Thực hiện tính toán biểu thức hoặc thống kê dữ liệu.
     * * @param request Chứa biểu thức (VD: "SUM(A)")
     * @return Kết quả số học (BigDecimal) hoặc lỗi nếu có.
     */
    CalculationResult calculate(String expression);
}
