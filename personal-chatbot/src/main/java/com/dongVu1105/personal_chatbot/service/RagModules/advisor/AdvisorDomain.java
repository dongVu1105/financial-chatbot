package com.dongVu1105.personal_chatbot.service.RagModules.advisor;

/**
 * Supported financial advisory domains for the slot-filling engine.
 */
public enum AdvisorDomain {
    DEBT_MANAGEMENT,
    BIG_PURCHASE,
    INSURANCE_PROTECTION,
    RETIREMENT_PLANNING,
    BUDGETING;

    /**
     * Helper to map loosely formatted user intent strings (e.g. "debt_advice")
     * to a canonical domain, for easier integration with upstream components.
     */
    public static AdvisorDomain fromIntent(String userIntent) {
        if (userIntent == null) {
            return null;
        }
        String normalized = userIntent.trim().toLowerCase();
        return switch (normalized) {
            case "debt", "debt_advice", "debt_management" -> DEBT_MANAGEMENT;
            case "big_purchase", "house_purchase", "car_purchase", "big_purchase_advice" -> BIG_PURCHASE;
            case "insurance", "insurance_protection", "protection" -> INSURANCE_PROTECTION;
            case "retirement", "retirement_planning" -> RETIREMENT_PLANNING;
            case "budget", "budgeting" -> BUDGETING;
            default -> null;
        };
    }
}


