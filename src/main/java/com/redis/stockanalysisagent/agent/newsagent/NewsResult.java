package com.redis.stockanalysisagent.agent.newsagent;

public class NewsResult {

    public enum FinishReason {
        COMPLETED,
        ERROR
    }

    private FinishReason finishReason;
    private String message;
    private NewsSnapshot finalResponse;

    public NewsResult() {
    }

    public NewsResult(FinishReason finishReason, String message, NewsSnapshot finalResponse) {
        this.finishReason = finishReason;
        this.message = message;
        this.finalResponse = finalResponse;
    }

    public static NewsResult completed(NewsSnapshot finalResponse) {
        return completed(null, finalResponse);
    }

    public static NewsResult completed(String message, NewsSnapshot finalResponse) {
        return new NewsResult(FinishReason.COMPLETED, message, finalResponse);
    }

    public static NewsResult error(String message) {
        return new NewsResult(FinishReason.ERROR, message, null);
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

    public NewsSnapshot getFinalResponse() {
        return finalResponse;
    }

    public void setFinalResponse(NewsSnapshot finalResponse) {
        this.finalResponse = finalResponse;
    }
}
