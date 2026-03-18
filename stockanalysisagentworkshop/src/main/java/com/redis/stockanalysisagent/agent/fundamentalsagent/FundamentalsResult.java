package com.redis.stockanalysisagent.agent.fundamentalsagent;

import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;

public class FundamentalsResult {

    public enum FinishReason {
        COMPLETED,
        ERROR
    }

    private FinishReason finishReason;
    private String message;
    private FundamentalsSnapshot finalResponse;
    private TokenUsageSummary tokenUsage;

    public FundamentalsResult() {
    }

    public FundamentalsResult(FinishReason finishReason, String message, FundamentalsSnapshot finalResponse) {
        this.finishReason = finishReason;
        this.message = message;
        this.finalResponse = finalResponse;
    }

    public static FundamentalsResult completed(FundamentalsSnapshot finalResponse) {
        return completed(null, finalResponse);
    }

    public static FundamentalsResult completed(String message, FundamentalsSnapshot finalResponse) {
        return new FundamentalsResult(FinishReason.COMPLETED, message, finalResponse);
    }

    public static FundamentalsResult error(String message) {
        return new FundamentalsResult(FinishReason.ERROR, message, null);
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

    public FundamentalsSnapshot getFinalResponse() {
        return finalResponse;
    }

    public void setFinalResponse(FundamentalsSnapshot finalResponse) {
        this.finalResponse = finalResponse;
    }

    public TokenUsageSummary getTokenUsage() {
        return tokenUsage;
    }

    public void setTokenUsage(TokenUsageSummary tokenUsage) {
        this.tokenUsage = tokenUsage;
    }
}
