# Part 0: Spring AI Intro

Before we build a multi-agent system, we need a simple mental model for Spring AI.

This file is not the workshop implementation. It is only a guided introduction with small example snippets.

By the end of this part, you should be comfortable with these ideas:

- `ChatModel`
- `ChatClient`
- the minimum prompt
- structured output
- tools
- memory
- advisors

## The Big Picture

For this workshop, the most useful mental model is:

1. `ChatModel` is the model connection.
2. `ChatClient` is the API we use in application code.
3. A prompt is the message we send.
4. Structured output lets us map responses into Java types.
5. Tools let the model call Java code.
6. Memory lets the model see earlier messages.
7. Advisors modify how requests and responses behave.

If you remember that, the rest of Spring AI starts to feel much simpler.

## 1. What Are `ChatModel` and `ChatClient`?

`ChatModel` is the lower-level Spring AI abstraction for a chat model.

It answers the question:

"How do I talk to the model provider at all?"

Most of the time, we do not want to work directly with `ChatModel`. We want a nicer API for:

- building prompts
- adding system and user messages
- attaching tools
- attaching advisors
- mapping structured output

That is what `ChatClient` is for.

So the practical difference is:

- `ChatModel` = model integration
- `ChatClient` = the fluent API we usually write in our services

Example:

```java
@Configuration
public class SimpleChatConfig {

    @Bean
    ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
```

What to remember:

- Spring Boot usually wires the `ChatModel` for you
- you usually build one or more `ChatClient` beans on top of it

## 2. What Is the Smallest Useful Prompt?

The minimum useful Spring AI call is just a user message.

Example:

```java
String answer = chatClient.prompt()
        .user("What does RSI measure?")
        .call()
        .content();
```

That is the smallest complete flow:

1. Start a prompt with `prompt()`
2. Add a message with `user(...)`
3. Execute with `call()`
4. Read the text with `content()`

You can also add a system message when you want to shape behavior.

Example:

```java
String answer = chatClient.prompt()
        .system("You are a helpful stock-analysis assistant.")
        .user("Explain price-to-earnings ratio in one short paragraph.")
        .call()
        .content();
```

What to remember:

- `system(...)` defines behavior
- `user(...)` contains the actual task

In this workshop, we will usually put the stable behavior in the agent configuration and the request-specific input in the service.

## 3. What Is Structured Output?

In many applications, raw text is not enough.

Sometimes we do not want:

- "Apple looks strong and margins seem healthy"

We want something more reliable, like:

- a Java object with named fields
- an enum for control flow
- a typed result we can safely pass to the next step

That is what structured output gives us.

In Spring AI, we can ask the model to return a response that maps into a Java class or record.

Example:

```java
public class RoutingDecision {

    private FinishReason finishReason;
    private String resolvedTicker;
    private String resolvedQuestion;

    public enum FinishReason {
        COMPLETED,
        NEEDS_MORE_INPUT,
        OUT_OF_SCOPE
    }

    // getters and setters
}
```

Then we can ask Spring AI to map the model response into that type:

```java
RoutingDecision decision = chatClient.prompt()
        .user("Analyze Apple stock")
        .call()
        .responseEntity(RoutingDecision.class)
        .entity();
```

This matters a lot for agents, because agents often need to return:

- finish reasons
- follow-up questions
- typed data contracts
- structured results for downstream agents

What to remember:

- plain text is for reading
- structured output is for control flow and inter-agent handoffs

## 4. What Is a Tool?

A tool is a normal Java method that the model can call.

In Spring AI, tools are usually created with `@Tool`.

Example:

```java
@Component
public class MarketDataTools {

    @Tool(description = "Fetch the latest market snapshot for a stock ticker.")
    public MarketSnapshot getMarketSnapshot(
            @ToolParam(description = "The stock ticker symbol in uppercase, for example AAPL.")
            String ticker
    ) {
        return new MarketSnapshot(
                ticker,
                new BigDecimal("214.10"),
                new BigDecimal("210.25")
        );
    }
}
```

What matters here:

- the tool is just Java
- the model does not fetch the data itself
- your application still owns the real logic

That is a very important idea for agents. The model reasons about what it needs, but your code still controls the real work.

## 5. How Does a `ChatClient` Get Access to Tools?

There are two common patterns.

### Option A: give tools to the client by default

Use this when an agent should always have the same tool set.

Example:

```java
@Configuration
public class MarketDataAgentConfig {

    @Bean
    ChatClient marketDataChatClient(ChatModel chatModel, MarketDataTools marketDataTools) {
        return ChatClient.builder(chatModel)
                .defaultSystem("You are the Market Data Agent.")
                .defaultTools(marketDataTools)
                .build();
    }
}
```

### Option B: give tools only for one request

Use this when the tool should be available only on a specific call.

Example:

```java
String answer = chatClient.prompt()
        .user("Get the latest market snapshot for AAPL.")
        .tools(marketDataTools)
        .call()
        .content();
```

What to remember:

- `defaultTools(...)` is good for agent-specific clients
- `.tools(...)` is good for one-off prompt calls

In this workshop, we will usually prefer `defaultTools(...)` because each agent has a stable responsibility.

## 6. What Is `ChatMemoryRepository`?

When people say "chat repository" in Spring AI, the relevant abstraction is usually `ChatMemoryRepository`.

`ChatMemoryRepository` is the storage layer for chat messages.

It answers the question:

"Where does the conversation history live?"

For a very simple setup, Spring AI provides `InMemoryChatMemoryRepository`.

Example:

```java
@Bean
ChatMemoryRepository chatMemoryRepository() {
    return new InMemoryChatMemoryRepository();
}
```

What to remember:

- `ChatMemoryRepository` stores and retrieves messages
- it does not decide how much history goes into the prompt

That second responsibility belongs to `ChatMemory`.

## 7. What Is `ChatMemory`?

`ChatMemory` is the runtime memory abstraction used by the client.

It answers the question:

"How much conversation history should the model see?"

A very common implementation is `MessageWindowChatMemory`, which keeps only the latest messages.

Example:

```java
@Bean
ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
    return MessageWindowChatMemory.builder()
            .chatMemoryRepository(chatMemoryRepository)
            .maxMessages(20)
            .build();
}
```

So the split is:

- `ChatMemoryRepository` = where messages are stored
- `ChatMemory` = what slice of that history is used

That distinction is easy to miss at first, but it is important.

## 8. How Do We Give a `ChatClient` Memory?

We do not attach memory to the client directly. We usually attach it through an advisor.

Example:

```java
@Bean
ChatClient coordinatorChatClient(ChatModel chatModel, ChatMemory chatMemory) {
    return ChatClient.builder(chatModel)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build();
}
```

Then, at runtime, we pass a conversation ID:

Example:

```java
String conversationId = "session-123";

String answer = chatClient.prompt()
        .user("Should I compare Apple and Microsoft margins?")
        .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
        .call()
        .content();
```

Why this matters:

- without a conversation ID, memory has nothing to look up
- with a conversation ID, Spring AI knows which chat history to load

## 9. What Are Advisors?

Advisors are one of the most important Spring AI concepts.

They are best understood as request/response helpers around `ChatClient`.

They can:

- inject memory
- add context
- influence structured output
- inspect or modify requests and responses

A useful shortcut is:

- tools help the model act
- advisors help the client behave

### Example: memory advisor

```java
MessageChatMemoryAdvisor.builder(chatMemory).build()
```

This advisor loads the right conversation history and includes it in the prompt.

### Example: structured output advisor

```java
ChatClient.builder(chatModel)
        .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
        .build();
```

This helps when you want the model to return data that maps cleanly into a Java class or record.

In this workshop, many agents will use this advisor because they return typed results instead of plain text.

## 10. How These Pieces Fit Together

This is the basic shape we will keep reusing throughout the workshop:

```java
@Configuration
public class ExampleAgentConfig {

    @Bean
    ChatClient exampleChatClient(
            ChatModel chatModel,
            ChatMemory chatMemory,
            MarketDataTools marketDataTools
    ) {
        return ChatClient.builder(chatModel)
                .defaultSystem("You are a stock-analysis helper.")
                .defaultTools(marketDataTools)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
```

```java
@Service
public class ExampleAgent {

    private final ChatClient exampleChatClient;

    public ExampleAgent(ChatClient exampleChatClient) {
        this.exampleChatClient = exampleChatClient;
    }

    public String execute(String userMessage, String conversationId) {
        return exampleChatClient.prompt()
                .user(userMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
}
```

This pattern matters because each agent is really just a different combination of:

- prompt
- structured output
- tools
- memory
- advisors

Part 1 will build on top of exactly that idea.

## What You Should Be Comfortable With Before Part 1

Before moving on, make sure these ideas feel clear:

- `ChatModel` is the model abstraction
- `ChatClient` is the fluent API we write against
- prompts are just messages sent through the client
- structured output maps model responses into Java types
- tools are Java methods the model can call
- `ChatMemoryRepository` stores messages
- `ChatMemory` controls which messages are used
- advisors modify request/response behavior around the client

If those ideas feel natural, you are ready to move into orchestration.
