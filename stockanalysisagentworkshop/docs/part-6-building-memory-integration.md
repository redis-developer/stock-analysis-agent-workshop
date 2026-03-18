# Part 6: Building Memory Integration

In this part, you will build the two Spring AI extension points that make memory work in this application:

- a custom `ChatMemoryRepository`
- a custom advisor

You should only work on these files:

- `src/main/java/com/redis/stockanalysisagent/memory/AmsChatMemoryRepository.java`
- `src/main/java/com/redis/stockanalysisagent/memory/LongTermMemoryAdvisor.java`

The rest of the memory stack is already in place.

That means:

- the Redis memory client is already configured
- the `AgentMemoryService` already talks to Agent Memory Server
- the memory beans are already wired into the application

Your job in this part is to connect Spring AI to that memory infrastructure.

## What You Are Building

By the end of this part, the system should be able to:

1. load prior working-memory messages for a conversation
2. save new conversation turns back into working memory
3. retrieve relevant long-term memories before routing
4. inject those memories into the current request as supplemental context

This is what makes the multi-agent system stateful across turns.

## Before You Start: The Two Spring AI Extension Points

This part only makes sense if you understand the two extension points you are about to implement.

They solve different memory problems.

- `ChatMemoryRepository` handles short-term conversation persistence
- an advisor handles request-time memory retrieval

Those are not the same thing.

## 1. What `ChatMemoryRepository` Does

`ChatMemoryRepository` is the storage adapter behind Spring AI chat memory.

It answers this question:

"When Spring AI wants the recent conversation, where should it load it from, and where should it save it?"

In this application, Spring AI does not store conversation history directly in a local list or in the model prompt.

Instead, it goes through:

```text
Spring AI ChatMemory
    |
    v
ChatMemoryRepository
    |
    v
Agent Memory Server
    |
    v
Redis
```

That means your custom repository is the bridge between Spring AI's message model and the working-memory model exposed by Agent Memory Server.

Conceptually, this repository has two jobs:

- load prior messages for the current conversation
- persist new messages after each turn

That is why the two most important methods are:

- `findByConversationId(...)`
- `saveAll(...)`

### Why This Matters in a Multi-Agent System

Without working memory, the coordinator has no recent conversation to build on.

That means follow-up questions like:

- "What about Microsoft instead?"
- "Use the same criteria as before."

would arrive with no usable session context.

The custom repository is what gives the coordinator access to recent conversation state without making the coordinator itself manage storage.

## 2. What an Advisor Does

An advisor is a Spring AI hook that runs around a model call.

It can do work:

- before the request is sent
- after the response comes back

In this workshop, the important hook is `before(...)`.

That hook lets your application inspect the request and modify it before the model sees it.

Conceptually, the advisor sits here:

```text
Current user message
    |
    v
Advisor before(...)
    |
    v
Model call
```

In this system, that is exactly where long-term memory belongs.

The advisor:

- reads the current conversation context
- searches long-term memory for relevant items
- augments the current request with those memories as supplemental background

So the advisor does not persist memory.

It retrieves memory and injects it into the request at the right moment.

### Why This Matters in a Multi-Agent System

The coordinator is the first model-driven decision point in the system.

If long-term memory is going to help with:

- omitted references
- user preferences
- continuity across turns

it has to be available before the coordinator routes the request.

That is why retrieval belongs in an advisor instead of inside one specialist agent or inside orchestration.

## 3. Why We Need Both

If you only had a `ChatMemoryRepository`, the system could remember the recent conversation, but it would not retrieve relevant long-term memories into the current request.

If you only had an advisor, the system could inject retrieved memories, but there would be no reliable short-term conversation store behind Spring AI chat memory.

We need both because they serve different layers:

- repository = persistence for working memory
- advisor = retrieval for long-term memory

That combination gives the system a clean production-ready memory flow:

```text
Conversation turns
    |
    v
ChatMemoryRepository
    |
    v
Working memory

Current request
    |
    v
Advisor before(...)
    |
    v
Long-term memory retrieval
    |
    v
Coordinator
```

This is the architecture you are implementing in this part.

## Step 1: Implement `findByConversationId(...)`

Open:

`src/main/java/com/redis/stockanalysisagent/memory/AmsChatMemoryRepository.java`

Find this method:

```java
@Override
public List<Message> findByConversationId(String conversationId) {
    // PART 6 STEP 1:
    // Replace this method body with the snippet from the Part 6 guide.
    return List.of();
}
```

Replace the method body with this exact code:

```java
WorkingMemoryResponse response = loadWorkingMemory(conversationId);
if (response == null || response.getMessages() == null) {
    return List.of();
}

List<Message> messages = new ArrayList<>();
for (MemoryMessage msg : response.getMessages()) {
    Message springMessage = convertToSpringMessage(msg);
    if (springMessage != null) {
        messages.add(springMessage);
    }
}
return messages;
```

Why you did this:

- Spring AI asks `ChatMemoryRepository` for the current conversation history
- Agent Memory Server stores that history in its own working-memory format
- your repository is the adapter between those two worlds

What this code is doing:

- it loads the current conversation from working memory
- it converts Agent Memory Server messages into Spring AI `Message` objects
- it returns the message list Spring AI needs to restore short-term conversation context

## Step 2: Implement `saveAll(...)`

In the same file, find this method:

```java
@Override
public void saveAll(String conversationId, List<Message> messages) {
    // PART 6 STEP 2:
    // Replace this method body with the snippet from the Part 6 guide.
}
```

Replace the method body with this exact code:

```java
String userId = parseUserId(conversationId);
String sessionId = parseSessionId(conversationId);
List<MemoryMessage> existingMessages = existingMessages(conversationId);
List<MemoryMessage> newMessages = toNewMessages(messages, existingMessages);

if (newMessages.isEmpty()) {
    return;
}

runSafely("save working memory for conversation " + conversationId, () -> {
    boolean firstMessage = existingMessages.isEmpty();
    agentMemoryService.appendMessagesToWorkingMemory(
            sessionId,
            newMessages,
            userId,
            MEMORY_MODEL
    );

    if (firstMessage) {
        applyTtl(sessionId, userId);
    }
});
```

Why you did this:

- memory is not useful unless new turns are written back after each interaction
- working memory should stay tied to both the user and the session
- this repository is where the application persists short-term conversation state

What this code is doing:

- it splits the conversation ID into `userId` and `sessionId`
- it converts Spring AI messages into Agent Memory Server messages
- it appends only new messages to working memory
- it applies a TTL the first time a session is created so working memory stays bounded

## Step 3: Implement the Advisor `before(...)` Hook

Open:

`src/main/java/com/redis/stockanalysisagent/memory/LongTermMemoryAdvisor.java`

Find this method:

```java
@Override
public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
    // PART 6 STEP 3:
    // Replace this method body with the snippet from the Part 6 guide.
    return request;
}
```

Replace the method body with this exact code:

```java
memoryRepository.setLastRetrievedMemories(List.of());

String conversationId = (String) request.context().get(ChatMemory.CONVERSATION_ID);
String userId = parseUserId(conversationId);

if (userId == null || ANONYMOUS_USER.equals(userId)) {
    return request;
}

String userMessage = request.prompt().getUserMessage().getText();
if (userMessage == null || userMessage.isBlank()) {
    return request;
}

if (!hasConversationHistory(conversationId)) {
    return request;
}

List<String> memories = searchMemories(userMessage, userId);
memoryRepository.setLastRetrievedMemories(memories);

if (memories.isEmpty()) {
    return request;
}

return request.mutate()
        .prompt(request.prompt().augmentUserMessage(existingUserMessage ->
                new UserMessage(augmentUserMessage(existingUserMessage.getText(), memories))))
        .context(RETRIEVED_MEMORIES, memories)
        .build();
```

Why you did this:

- long-term memory should influence the request before the coordinator routes it
- advisors are the Spring AI extension point for request-time context injection
- this keeps memory retrieval explicit and code-driven instead of hiding it inside a prompt

What this code is doing:

- it reads the current conversation and user context
- it skips retrieval when there is no meaningful session history
- it searches long-term memory using the current user message
- it stores the retrieved memories for the UI layer
- it augments the user message with supplemental background memory before the model call runs

## Why This Design Fits a Production-Ready Multi-Agent System

You did not add memory by making every agent manually talk to Redis.

You added memory at the right integration points:

- `ChatMemoryRepository` for working-memory persistence
- advisor `before(...)` for long-term memory retrieval

That matters because:

- the coordinator can use memory without owning memory storage
- the specialist agents can stay focused on their own tasks
- orchestration still stays in application code
- memory stays explicit, reusable, and bounded

This is the production-ready pattern:

- persistence is handled by the repository
- retrieval is handled by the advisor
- routing and orchestration stay separate

## What “Done” Looks Like

You are done when you understand this flow:

1. Spring AI loads recent conversation turns through `AmsChatMemoryRepository`
2. the user sends a new message
3. `LongTermMemoryAdvisor.before(...)` looks for relevant long-term memories
4. the advisor injects those memories into the request as background context
5. the coordinator routes the request with continuity across turns
6. the application saves the new turn back into working memory

At that point, your multi-agent system is no longer only orchestrated.

It is also memory-aware.
