package com.redis.stockanalysisagent.agent.marketdataagent;

public class MarketDataResult {

    public enum FinishReason {
        COMPLETED,
        ERROR
    }

    private FinishReason finishReason;
    private String message;
    private MarketSnapshot finalResponse;

    public MarketDataResult() {
    }

    public MarketDataResult(FinishReason finishReason, String message, MarketSnapshot finalResponse) {
        this.finishReason = finishReason;
        this.message = message;
        this.finalResponse = finalResponse;
    }

    public static MarketDataResult completed(MarketSnapshot finalResponse) {
        return new MarketDataResult(FinishReason.COMPLETED, null, finalResponse);
    }

    public static MarketDataResult error(String message) {
        return new MarketDataResult(FinishReason.ERROR, message, null);
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

    public MarketSnapshot getFinalResponse() {
        return finalResponse;
    }

    public void setFinalResponse(MarketSnapshot finalResponse) {
        this.finalResponse = finalResponse;
    }
}
