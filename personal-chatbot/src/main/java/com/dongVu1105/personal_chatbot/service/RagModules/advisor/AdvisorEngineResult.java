package com.dongVu1105.personal_chatbot.service.RagModules.advisor;

import java.util.List;
import java.util.Map;

/**
 * Structured response from {@link AdvisorEngine}.
 *
 * <p>
 * This is intentionally generic so that the frontend / pipeline can easily
 * inspect:
 * </p>
 * <ul>
 * <li>status: whether more information is needed or advice is ready</li>
 * <li>followUpQuestions: concrete slot-filling questions to ask user</li>
 * <li>adviceSummary: human-readable mock advice</li>
 * <li>domainPayload: optional extra structured data per domain</li>
 * </ul>
 */
public class AdvisorEngineResult {

    private final AdvisorStatus status;
    private final AdvisorDomain domain;
    private final List<String> missingMetrics;
    private final List<String> followUpQuestions;
    private final String adviceSummary;
    private final Map<String, Object> domainPayload;

    public AdvisorEngineResult(AdvisorStatus status,
                               AdvisorDomain domain,
                               List<String> missingMetrics,
                               List<String> followUpQuestions,
                               String adviceSummary,
                               Map<String, Object> domainPayload) {
        this.status = status;
        this.domain = domain;
        this.missingMetrics = missingMetrics;
        this.followUpQuestions = followUpQuestions;
        this.adviceSummary = adviceSummary;
        this.domainPayload = domainPayload;
    }

    public AdvisorStatus getStatus() {
        return status;
    }

    public AdvisorDomain getDomain() {
        return domain;
    }

    public List<String> getMissingMetrics() {
        return missingMetrics;
    }

    public List<String> getFollowUpQuestions() {
        return followUpQuestions;
    }

    public String getAdviceSummary() {
        return adviceSummary;
    }

    public Map<String, Object> getDomainPayload() {
        return domainPayload;
    }
}


