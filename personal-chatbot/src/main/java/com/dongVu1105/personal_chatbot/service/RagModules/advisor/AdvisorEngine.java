package com.dongVu1105.personal_chatbot.service.RagModules.advisor;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core "Financial Advisor" engine implementing a simple slot-filling mechanism.
 *
 * <p>
 * Responsibilities:
 * </p>
 * <ul>
 * <li>Define financial domains and their mandatory metrics</li>
 * <li>Compare user-provided data with required metrics</li>
 * <li>Return follow-up questions when information is missing</li>
 * <li>Run mock calculation per domain when all metrics are present</li>
 * </ul>
 *
 * <p>
 * This class is intentionally self-contained and pure-logic so that it can be
 * easily unit tested and reused inside LLM / RAG pipelines.
 * </p>
 */
@Service
public class AdvisorEngine {

    // ===================== DOMAIN CONFIGURATION =====================

    /**
     * Map of domain -> ordered list of required metric keys.
     */
    private static final Map<AdvisorDomain, List<String>> REQUIRED_METRICS_BY_DOMAIN;

    /**
     * Map of domain+metric -> natural-language question used when metric is missing.
     */
    private static final Map<String, String> METRIC_QUESTIONS;

    static {
        Map<AdvisorDomain, List<String>> required = new EnumMap<>(AdvisorDomain.class);

        // Debt Management
        required.put(AdvisorDomain.DEBT_MANAGEMENT, List.of(
                "debt_list",
                "interest_rates",
                "outstanding_balance",
                "monthly_payment_capacity"
        ));

        // Big Purchase (House/Car)
        required.put(AdvisorDomain.BIG_PURCHASE, List.of(
                "total_cost",
                "down_payment",
                "monthly_income",
                "existing_debts",
                "loan_tenure"
        ));

        // Insurance Protection
        required.put(AdvisorDomain.INSURANCE_PROTECTION, List.of(
                "dependents",
                "monthly_income",
                "total_liabilities",
                "existing_coverage"
        ));

        // Retirement Planning
        required.put(AdvisorDomain.RETIREMENT_PLANNING, List.of(
                "current_age",
                "retirement_age",
                "desired_monthly_expenses",
                "current_savings",
                "risk_tolerance"
        ));

        // Budgeting
        required.put(AdvisorDomain.BUDGETING, List.of(
                "total_income",
                "fixed_costs",
                "savings_goal"
        ));

        REQUIRED_METRICS_BY_DOMAIN = Collections.unmodifiableMap(required);

        // Question templates for each metric (domain-specific where needed)
        Map<String, String> q = new HashMap<>();

        // DEBT_MANAGEMENT
        q.put(key(AdvisorDomain.DEBT_MANAGEMENT, "debt_list"),
                "Bạn có thể liệt kê chi tiết các khoản nợ hiện tại (ví dụ: thẻ tín dụng, vay tiêu dùng, vay mua nhà)?");
        q.put(key(AdvisorDomain.DEBT_MANAGEMENT, "interest_rates"),
                "Lãi suất cho từng khoản nợ của bạn là bao nhiêu phần trăm mỗi năm?");
        q.put(key(AdvisorDomain.DEBT_MANAGEMENT, "outstanding_balance"),
                "Tổng dư nợ hiện tại của bạn (tổng số tiền bạn còn nợ) là bao nhiêu?");
        q.put(key(AdvisorDomain.DEBT_MANAGEMENT, "monthly_payment_capacity"),
                "Mỗi tháng bạn có thể dành tối đa bao nhiêu tiền để trả nợ?");

        // BIG_PURCHASE
        q.put(key(AdvisorDomain.BIG_PURCHASE, "total_cost"),
                "Giá trị dự kiến của tài sản bạn muốn mua (nhà/xe) là khoảng bao nhiêu?");
        q.put(key(AdvisorDomain.BIG_PURCHASE, "down_payment"),
                "Hiện tại bạn có sẵn bao nhiêu tiền mặt để trả trước (down payment)?");
        q.put(key(AdvisorDomain.BIG_PURCHASE, "monthly_income"),
                "Thu nhập ròng hàng tháng của bạn (sau thuế) là khoảng bao nhiêu?");
        q.put(key(AdvisorDomain.BIG_PURCHASE, "existing_debts"),
                "Hiện bạn đang có những khoản nợ nào khác và mỗi tháng phải trả bao nhiêu?");
        q.put(key(AdvisorDomain.BIG_PURCHASE, "loan_tenure"),
                "Bạn dự định vay trong thời gian bao lâu (số năm)?");

        // INSURANCE_PROTECTION
        q.put(key(AdvisorDomain.INSURANCE_PROTECTION, "dependents"),
                "Hiện tại bạn đang có bao nhiêu người phụ thuộc tài chính (vợ/chồng, con cái, cha mẹ)?");
        q.put(key(AdvisorDomain.INSURANCE_PROTECTION, "monthly_income"),
                "Thu nhập ròng hàng tháng của bạn là khoảng bao nhiêu?");
        q.put(key(AdvisorDomain.INSURANCE_PROTECTION, "total_liabilities"),
                "Tổng các khoản nợ và nghĩa vụ tài chính hiện tại của bạn là bao nhiêu?");
        q.put(key(AdvisorDomain.INSURANCE_PROTECTION, "existing_coverage"),
                "Hiện tại bạn đã có những loại bảo hiểm nào và với số tiền bảo vệ khoảng bao nhiêu?");

        // RETIREMENT_PLANNING
        q.put(key(AdvisorDomain.RETIREMENT_PLANNING, "current_age"),
                "Hiện tại bạn bao nhiêu tuổi?");
        q.put(key(AdvisorDomain.RETIREMENT_PLANNING, "retirement_age"),
                "Bạn dự định nghỉ hưu ở độ tuổi nào?");
        q.put(key(AdvisorDomain.RETIREMENT_PLANNING, "desired_monthly_expenses"),
                "Khi nghỉ hưu bạn muốn có mức chi tiêu hàng tháng khoảng bao nhiêu?");
        q.put(key(AdvisorDomain.RETIREMENT_PLANNING, "current_savings"),
                "Hiện tại bạn đã tích lũy được khoảng bao nhiêu tiền cho mục tiêu nghỉ hưu?");
        q.put(key(AdvisorDomain.RETIREMENT_PLANNING, "risk_tolerance"),
                "Mức chấp nhận rủi ro của bạn khi đầu tư cho nghỉ hưu là như thế nào (thấp / vừa / cao)?");

        // BUDGETING
        q.put(key(AdvisorDomain.BUDGETING, "total_income"),
                "Tổng thu nhập hàng tháng của bạn (từ lương và các nguồn khác) là khoảng bao nhiêu?");
        q.put(key(AdvisorDomain.BUDGETING, "fixed_costs"),
                "Tổng chi phí cố định hàng tháng của bạn (tiền nhà, điện nước, nợ, v.v.) là khoảng bao nhiêu?");
        q.put(key(AdvisorDomain.BUDGETING, "savings_goal"),
                "Mỗi tháng bạn muốn tiết kiệm được khoảng bao nhiêu tiền?");

        METRIC_QUESTIONS = Collections.unmodifiableMap(q);
    }

    private static String key(AdvisorDomain domain, String metric) {
        return domain.name() + ":" + metric;
    }

    // ===================== PUBLIC API =====================

    /**
     * Main entry point for the Financial Advisor engine.
     *
     * @param userIntent free-form intent string, e.g. "debt_advice"
     * @param userData   map of known parameters extracted from conversation
     * @return structured {@link AdvisorEngineResult} indicating whether more
     *         information is needed or advice is ready.
     */
    public AdvisorEngineResult analyzeRequest(String userIntent, Map<String, Object> userData) {
        AdvisorDomain domain = AdvisorDomain.fromIntent(userIntent);
        if (domain == null) {
            // Unknown / unsupported advisory domain: treat as missing info with a generic prompt.
            return new AdvisorEngineResult(
                    AdvisorStatus.MISSING_INFO,
                    null,
                    List.of("domain"),
                    List.of("Bạn đang cần tư vấn về chủ đề nào? (quản lý nợ, mua nhà/xe, bảo hiểm, nghỉ hưu, hay ngân sách hàng tháng)"),
                    null,
                    null);
        }

        Map<String, Object> safeUserData = (userData != null) ? userData : Collections.emptyMap();

        List<String> requiredMetrics = REQUIRED_METRICS_BY_DOMAIN.getOrDefault(domain, List.of());
        List<String> missingMetrics = findMissingMetrics(requiredMetrics, safeUserData);

        if (!missingMetrics.isEmpty()) {
            List<String> questions = generateFollowUpQuestions(domain, missingMetrics);
            return new AdvisorEngineResult(
                    AdvisorStatus.MISSING_INFO,
                    domain,
                    missingMetrics,
                    questions,
                    null,
                    null);
        }

        // All metrics present: run mock domain-specific advisory logic.
        return generateAdvice(domain, safeUserData);
    }

    // ===================== INTERNAL HELPERS =====================

    private List<String> findMissingMetrics(List<String> requiredMetrics, Map<String, Object> userData) {
        List<String> missing = new ArrayList<>();
        for (String metric : requiredMetrics) {
            if (!userData.containsKey(metric) || userData.get(metric) == null) {
                missing.add(metric);
            }
        }
        return missing;
    }

    private List<String> generateFollowUpQuestions(AdvisorDomain domain, List<String> missingMetrics) {
        return missingMetrics.stream()
                .map(metric -> {
                    String k = key(domain, metric);
                    return METRIC_QUESTIONS.getOrDefault(
                            k,
                            "Bạn có thể cung cấp thêm thông tin về \"" + metric + "\" không?");
                })
                .collect(Collectors.toList());
    }

    private AdvisorEngineResult generateAdvice(AdvisorDomain domain, Map<String, Object> userData) {
        return switch (domain) {
            case DEBT_MANAGEMENT -> runDebtManagementAdvice(userData);
            case BIG_PURCHASE -> runBigPurchaseAdvice(userData);
            case INSURANCE_PROTECTION -> runInsuranceProtectionAdvice(userData);
            case RETIREMENT_PLANNING -> runRetirementPlanningAdvice(userData);
            case BUDGETING -> runBudgetingAdvice(userData);
        };
    }

    // ===================== MOCK DOMAIN CALCULATORS =====================

    /**
     * Mock advisory logic for Debt Management.
     *
     * <p>
     * Example behavior:
     * </p>
     * <ul>
     * <li>Suggest debt snowball vs avalanche strategy</li>
     * <li>Estimate months to pay off if all surplus goes to highest interest
     * debt</li>
     * </ul>
     */
    private AdvisorEngineResult runDebtManagementAdvice(Map<String, Object> userData) {
        Double balance = toDouble(userData.get("outstanding_balance"));
        Double capacity = toDouble(userData.get("monthly_payment_capacity"));

        StringBuilder sb = new StringBuilder();
        sb.append("Dựa trên thông tin về các khoản nợ và khả năng trả nợ hàng tháng, bạn nên ưu tiên trả trước ");
        sb.append("các khoản nợ có lãi suất cao nhất (chiến lược \"avalanche\"). ");

        if (balance != null && capacity != null && capacity > 0) {
            double approxMonths = balance / capacity;
            int roundedMonths = (int) Math.ceil(approxMonths);
            sb.append("Với tổng dư nợ khoảng ").append(Math.round(balance)).append(" và khả năng trả nợ ");
            sb.append(Math.round(capacity)).append(" mỗi tháng, ước tính bạn cần khoảng ");
            sb.append(roundedMonths).append(" tháng để trả hết nợ (chưa tính lãi phát sinh). ");
        }

        sb.append("Bạn cũng nên duy trì quỹ dự phòng 3-6 tháng chi tiêu để tránh phải vay thêm khi có tình huống khẩn cấp.");

        Map<String, Object> payload = Map.of(
                "strategy", "debt_avalanche",
                "total_outstanding", userData.get("outstanding_balance"),
                "monthly_capacity", userData.get("monthly_payment_capacity"));

        return new AdvisorEngineResult(
                AdvisorStatus.ADVICE_READY,
                AdvisorDomain.DEBT_MANAGEMENT,
                List.of(),
                List.of(),
                sb.toString(),
                payload);
    }

    /**
     * Mock advisory logic for Big Purchase (House / Car).
     */
    private AdvisorEngineResult runBigPurchaseAdvice(Map<String, Object> userData) {
        Double totalCost = toDouble(userData.get("total_cost"));
        Double downPayment = toDouble(userData.get("down_payment"));
        Double monthlyIncome = toDouble(userData.get("monthly_income"));

        StringBuilder sb = new StringBuilder();
        sb.append("Đối với khoản mua lớn (nhà/xe), bạn nên đảm bảo tỷ lệ trả góp hàng tháng không vượt quá 30-40% thu nhập ròng. ");

        if (totalCost != null && downPayment != null) {
            double ltv = (totalCost - downPayment) / totalCost * 100;
            sb.append("Tỷ lệ vay trên giá trị tài sản (LTV) ước tính khoảng ")
                    .append(Math.round(ltv))
                    .append("%, ");
            if (ltv > 80) {
                sb.append("mức này khá cao, bạn nên cân nhắc tăng số tiền trả trước hoặc chọn tài sản giá thấp hơn. ");
            } else {
                sb.append("mức này tương đối an toàn trong nhiều trường hợp. ");
            }
        }

        if (monthlyIncome != null) {
            double safeInstallment = monthlyIncome * 0.35;
            sb.append("Với thu nhập hàng tháng khoảng ")
                    .append(Math.round(monthlyIncome))
                    .append(", khoản trả góp an toàn nên dưới khoảng ")
                    .append(Math.round(safeInstallment))
                    .append(" mỗi tháng.");
        }

        Map<String, Object> payload = Map.of(
                "ltv_ratio", totalCost != null && downPayment != null && totalCost > 0
                        ? (totalCost - downPayment) / totalCost
                        : null,
                "safe_installment_ceiling",
                monthlyIncome != null ? monthlyIncome * 0.35 : null);

        return new AdvisorEngineResult(
                AdvisorStatus.ADVICE_READY,
                AdvisorDomain.BIG_PURCHASE,
                List.of(),
                List.of(),
                sb.toString(),
                payload);
    }

    /**
     * Mock advisory logic for Insurance Protection.
     */
    private AdvisorEngineResult runInsuranceProtectionAdvice(Map<String, Object> userData) {
        Integer dependents = toInteger(userData.get("dependents"));
        Double income = toDouble(userData.get("monthly_income"));
        Double liabilities = toDouble(userData.get("total_liabilities"));

        StringBuilder sb = new StringBuilder();
        sb.append("Để bảo vệ tài chính cho gia đình, bạn nên cân nhắc tổng mức bảo hiểm nhân thọ ");
        sb.append("xấp xỉ 10-15 lần thu nhập hàng năm, cộng thêm các khoản nợ hiện có. ");

        Double suggestedCoverage = null;
        if (income != null) {
            double annualIncome = income * 12;
            suggestedCoverage = annualIncome * 12; // 12x annual income as mid-point
            if (liabilities != null) {
                suggestedCoverage += liabilities;
            }
            sb.append("Với thu nhập hàng tháng khoảng ")
                    .append(Math.round(income))
                    .append(", mức bảo hiểm gợi ý tối thiểu khoảng ")
                    .append(Math.round(suggestedCoverage))
                    .append(". ");
        }

        if (dependents != null && dependents > 0) {
            sb.append("Vì bạn có ").append(dependents)
                    .append(" người phụ thuộc, hãy ưu tiên gói bảo hiểm đảm bảo thu nhập tối thiểu cho họ trong nhiều năm nếu có rủi ro.");
        }

        Map<String, Object> payload = Map.of(
                "suggested_coverage", suggestedCoverage,
                "dependents", dependents);

        return new AdvisorEngineResult(
                AdvisorStatus.ADVICE_READY,
                AdvisorDomain.INSURANCE_PROTECTION,
                List.of(),
                List.of(),
                sb.toString(),
                payload);
    }

    /**
     * Mock advisory logic for Retirement Planning.
     */
    private AdvisorEngineResult runRetirementPlanningAdvice(Map<String, Object> userData) {
        Integer currentAge = toInteger(userData.get("current_age"));
        Integer retirementAge = toInteger(userData.get("retirement_age"));
        Double desiredMonthly = toDouble(userData.get("desired_monthly_expenses"));
        Double currentSavings = toDouble(userData.get("current_savings"));

        StringBuilder sb = new StringBuilder();
        sb.append("Kế hoạch nghỉ hưu nên đảm bảo bạn có đủ tài sản để chi trả chi tiêu hàng tháng trong ít nhất 20-30 năm sau khi nghỉ. ");

        Integer yearsToRetirement = null;
        if (currentAge != null && retirementAge != null) {
            yearsToRetirement = retirementAge - currentAge;
            if (yearsToRetirement < 0) {
                yearsToRetirement = 0;
            }
        }

        Double targetCorpus = null;
        if (desiredMonthly != null) {
            targetCorpus = desiredMonthly * 12 * 25; // 25x annual expenses rule of thumb
            sb.append("Với mong muốn chi tiêu khoảng ")
                    .append(Math.round(desiredMonthly))
                    .append(" mỗi tháng, tổng tài sản mục tiêu khi nghỉ hưu nên vào khoảng ")
                    .append(Math.round(targetCorpus))
                    .append(". ");
        }

        Double requiredMonthlySaving = null;
        if (targetCorpus != null && yearsToRetirement != null && yearsToRetirement > 0) {
            // Very rough linear saving approximation (no investment growth)
            requiredMonthlySaving = targetCorpus / (yearsToRetirement * 12.0);
            sb.append("Để đạt được mục tiêu này trong khoảng ")
                    .append(yearsToRetirement)
                    .append(" năm nữa, bạn cần tiết kiệm trung bình khoảng ")
                    .append(Math.round(requiredMonthlySaving))
                    .append(" mỗi tháng (chưa tính lợi nhuận đầu tư). ");
        }

        if (currentSavings != null) {
            sb.append("Hiện bạn đã tích lũy khoảng ")
                    .append(Math.round(currentSavings))
                    .append(", hãy cân nhắc đầu tư đa dạng (trái phiếu, cổ phiếu, quỹ) phù hợp với mức chấp nhận rủi ro của bạn.");
        }

        Map<String, Object> payload = Map.of(
                "target_corpus", targetCorpus,
                "years_to_retirement", yearsToRetirement,
                "required_monthly_saving", requiredMonthlySaving);

        return new AdvisorEngineResult(
                AdvisorStatus.ADVICE_READY,
                AdvisorDomain.RETIREMENT_PLANNING,
                List.of(),
                List.of(),
                sb.toString(),
                payload);
    }

    /**
     * Mock advisory logic for Budgeting.
     */
    private AdvisorEngineResult runBudgetingAdvice(Map<String, Object> userData) {
        Double totalIncome = toDouble(userData.get("total_income"));
        Double fixedCosts = toDouble(userData.get("fixed_costs"));
        Double savingsGoal = toDouble(userData.get("savings_goal"));

        StringBuilder sb = new StringBuilder();
        sb.append("Một nguyên tắc phổ biến là chia ngân sách theo tỷ lệ 50/30/20 (nhu cầu/cần thiết, mong muốn, tiết kiệm). ");

        Double variableSpending = null;
        if (totalIncome != null && fixedCosts != null) {
            variableSpending = totalIncome - fixedCosts - savingsGoal;
            sb.append("Với thu nhập khoảng ")
                    .append(Math.round(totalIncome))
                    .append(" và chi phí cố định khoảng ")
                    .append(Math.round(fixedCosts))
                    .append(", sau khi dành cho mục tiêu tiết kiệm, phần còn lại nên được giới hạn cho chi tiêu linh hoạt. ");
        }

        if (savingsGoal != null && totalIncome != null) {
            double savingsRate = (savingsGoal / totalIncome) * 100;
            sb.append("Mục tiêu tiết kiệm hiện tại tương đương khoảng ")
                    .append(Math.round(savingsRate))
                    .append("% thu nhập hàng tháng. Hãy duy trì hoặc tăng dần tỷ lệ này nếu có thể.");
        }

        Map<String, Object> payload = Map.of(
                "estimated_variable_spending", variableSpending,
                "savings_rate",
                (totalIncome != null && savingsGoal != null && totalIncome > 0)
                        ? savingsGoal / totalIncome
                        : null);

        return new AdvisorEngineResult(
                AdvisorStatus.ADVICE_READY,
                AdvisorDomain.BUDGETING,
                List.of(),
                List.of(),
                sb.toString(),
                payload);
    }

    // ===================== SIMPLE CAST HELPERS =====================

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}


