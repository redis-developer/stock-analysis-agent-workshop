package com.redis.stockanalysisagent.memory.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public final class AgentMemoryApiModels {

    private AgentMemoryApiModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HealthResponse(
            @JsonProperty("status") String status,
            @JsonProperty("version") String version
    ) {
    }

    public enum MessageRole {
        @JsonProperty("USER")
        USER,
        @JsonProperty("ASSISTANT")
        ASSISTANT,
        @JsonProperty("SYSTEM")
        SYSTEM
    }

    public enum MemoryType {
        @JsonProperty("semantic")
        SEMANTIC,
        @JsonProperty("episodic")
        EPISODIC,
        @JsonProperty("message")
        MESSAGE
    }

    public enum FilterConjunction {
        @JsonProperty("all")
        ALL,
        @JsonProperty("any")
        ANY
    }

    public record ContentPart(
            @JsonProperty("text") String text
    ) {
        public static ContentPart text(String value) {
            return new ContentPart(value);
        }
    }

    public record AddSessionEventRequest(
            @JsonProperty("actorId") String actorId,
            @JsonProperty("role") MessageRole role,
            @JsonProperty("content") List<ContentPart> content,
            @JsonProperty("createdAt") long createdAt,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddSessionEventResponse(
            @JsonProperty("event") SessionEvent event
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("actorId") String actorId,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("role") MessageRole role,
            @JsonProperty("content") List<ContentPart> content,
            @JsonProperty("createdAt") long createdAt,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionMemory(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("ownerId") String ownerId,
            @JsonProperty("events") List<SessionEvent> events
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GetSessionEventResponse(
            @JsonProperty("event") SessionEvent event
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ListSessionsResponse(
            @JsonProperty("sessions") List<String> sessions,
            @JsonProperty("total") int total
    ) {
    }

    public record CreateMemoryRecord(
            @JsonProperty("id") String id,
            @JsonProperty("text") String text,
            @JsonProperty("memoryType") MemoryType memoryType,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("ownerId") String ownerId,
            @JsonProperty("namespace") String namespace,
            @JsonProperty("topics") List<String> topics
    ) {
    }

    public record BulkCreateLongTermMemoriesRequest(
            @JsonProperty("memories") List<CreateMemoryRecord> memories
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BulkOperationError(
            @JsonProperty("id") String id,
            @JsonProperty("message") String message
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BulkCreateLongTermMemoriesResponse(
            @JsonProperty("created") List<String> created,
            @JsonProperty("errors") List<BulkOperationError> errors
    ) {
    }

    public record BulkDeleteLongTermMemoriesRequest(
            @JsonProperty("memoryIds") List<String> memoryIds
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BulkDeleteLongTermMemoriesResponse(
            @JsonProperty("deleted") List<String> deleted,
            @JsonProperty("errors") List<BulkOperationError> errors
    ) {
    }

    public record TagFilter(
            @JsonProperty("eq") String eq,
            @JsonProperty("ne") String ne,
            @JsonProperty("in") List<String> in,
            @JsonProperty("all") List<String> all
    ) {
        public static TagFilter eq(String value) {
            return new TagFilter(value, null, null, null);
        }
    }

    public record NumericFilter(
            @JsonProperty("gt") Long gt,
            @JsonProperty("lt") Long lt,
            @JsonProperty("gte") Long gte,
            @JsonProperty("lte") Long lte,
            @JsonProperty("eq") Long eq
    ) {
    }

    public record LongTermMemoryFilter(
            @JsonProperty("sessionId") TagFilter sessionId,
            @JsonProperty("ownerId") TagFilter ownerId,
            @JsonProperty("namespace") TagFilter namespace,
            @JsonProperty("topics") TagFilter topics,
            @JsonProperty("memoryType") TagFilter memoryType,
            @JsonProperty("createdAt") NumericFilter createdAt
    ) {
    }

    public record SearchLongTermMemoryRequest(
            @JsonProperty("text") String text,
            @JsonProperty("similarityThreshold") Double similarityThreshold,
            @JsonProperty("filter") LongTermMemoryFilter filter,
            @JsonProperty("filterOp") FilterConjunction filterOp,
            @JsonProperty("limit") Integer limit,
            @JsonProperty("pageToken") String pageToken
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LongTermMemoryRecord(
            @JsonProperty("id") String id,
            @JsonProperty("text") String text,
            @JsonProperty("createdAt") long createdAt,
            @JsonProperty("updatedAt") long updatedAt,
            @JsonProperty("memoryType") MemoryType memoryType,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("ownerId") String ownerId,
            @JsonProperty("namespace") String namespace,
            @JsonProperty("topics") List<String> topics
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchLongTermMemoryResponse(
            @JsonProperty("memories") List<LongTermMemoryRecord> memories,
            @JsonProperty("nextPageToken") String nextPageToken
    ) {
    }

    public record UpdateLongTermMemoryRequest(
            @JsonProperty("text") String text,
            @JsonProperty("memoryType") MemoryType memoryType,
            @JsonProperty("topics") List<String> topics,
            @JsonProperty("namespace") String namespace,
            @JsonProperty("ownerId") String ownerId,
            @JsonProperty("sessionId") String sessionId
    ) {
    }
}
