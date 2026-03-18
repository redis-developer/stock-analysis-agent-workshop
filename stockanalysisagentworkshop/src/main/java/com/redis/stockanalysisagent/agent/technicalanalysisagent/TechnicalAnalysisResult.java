package com.redis.stockanalysisagent.agent.technicalanalysisagent;

import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;

public class TechnicalAnalysisResult {

    public enum FinishReason {
        COMPLETED,
        ERROR
    }

    private FinishReason finishReason;
    private String message;
    private TechnicalAnalysisSnapshot finalResponse;
    private TokenUsageSummary tokenUsage;

    public TechnicalAnalysisResult() {
    }

    public TechnicalAnalysisResult(
            FinishReason finishReason,
            String message,
            TechnicalAnalysisSnapshot finalResponse
    ) {
        this.finishReason = finishReason;
        this.message = message;
        this.finalResponse = finalResponse;
    }

    public static TechnicalAnalysisResult completed(TechnicalAnalysisSnapshot finalResponse) {
        return completed(null, finalResponse);
    }

    public static TechnicalAnalysisResult completed(String message, TechnicalAnalysisSnapshot finalResponse) {
        return new TechnicalAnalysisResult(FinishReason.COMPLETED, message, finalResponse);
    }

    public static TechnicalAnalysisResult error(String message) {
        return new TechnicalAnalysisResult(FinishReason.ERROR, message, null);
    }

    public FinishReason getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(FinishReason finishReason) {
        this.finishReason = finishReason;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public TechnicalAnalysisSnapshot getFinalResponse() {
        return finalResponse;
    }

    public void setFinalResponse(TechnicalAnalysisSnapshot finalResponse) {
        this.finalResponse = finalResponse;
    }

    public TokenUsageSummary getTokenUsage() {
        return tokenUsage;
    }

    public void setTokenUsage(TokenUsageSummary tokenUsage) {
        this.tokenUsage = tokenUsage;
    }
}
