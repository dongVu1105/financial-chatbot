package com.dongVu1105.personal_chatbot.service.RagModules.advisor;

/**
 * High level status for the AdvisorEngine result.
 */
public enum AdvisorStatus {
    /**
     * Some required metrics are missing, the engine will return follow-up
     * questions to ask the user.
     */
    MISSING_INFO,

    /**
     * All mandatory metrics are present and the engine has produced a
     * (mock) advisory result.
     */
    ADVICE_READY
}


