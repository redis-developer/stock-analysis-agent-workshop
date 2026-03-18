# Part 2: Implementing Your First Agent

In this part, you will implement the `MarketDataAgent`.

You should only work on these files:

- `src/main/java/com/redis/stockanalysisagent/agent/tools/MarketDataTools.java`
- `src/main/java/com/redis/stockanalysisagent/agent/marketdataagent/MarketDataAgentConfig.java`
- `src/main/java/com/redis/stockanalysisagent/agent/marketdataagent/MarketDataAgent.java`

The rest of the scaffold is already in place.

## What You Are Building

By the end of this part, your agent should:

- receive a ticker and a user question
- use a tool to fetch market data
- return a structured `MarketDataResult`

## Step 1: Implement the Tool

Open:

`src/main/java/com/redis/stockanalysisagent/agent/tools/MarketDataTools.java`

Inside the class, find this comment:

```java
// PART 2 STEP 1:
// Paste the getMarketSnapshot(...) tool method from the Part 2 guide here.
```

Replace that comment with this exact method:

```java
@Tool(description = "Fetch the latest market snapshot for a stock ticker.")
public MarketSnapshot getMarketSnapshot(
        @ToolParam(description = "The stock ticker symbol in uppercase, for example AAPL.")
        String ticker
) {
    return marketDataProvider.fetchSnapshot(ticker);
}
```

Why you did this:

- the model should not fetch market data on its own
- your application owns the actual data access
- the tool is how Spring AI exposes Java functionality to the model

What this code is doing:

- it exposes market data as a capability the agent can call through the model
- it keeps data fetching in application code instead of in the prompt
- it returns a structured `MarketSnapshot` the agent can use in its response

## Step 2: Implement the `ChatClient` Configuration

Open:

`src/main/java/com/redis/stockanalysisagent/agent/marketdataagent/MarketDataAgentConfig.java`

### Step 2A: replace the prompt

Find this field:

```java
private static final String DEFAULT_PROMPT = """
        PART 2 TODO:
        Replace this placeholder with the default prompt snippet from the Part 2 guide.
        """;
```

Replace it with this exact field:

```java
private static final String DEFAULT_PROMPT = """
        ROLE
        You are the Market Data Agent for a stock-analysis system.

        RESPONSIBILITY
        Use the available tools to fetch current market data for the requested ticker and return a grounded result.

        RULES
        - Always use the market-data tools before returning a completed result.
        - Never invent prices, percentages, timestamps, or sources.
        - Use the exact tool result to populate finalResponse.
        - Keep message concise and directly useful to the user.
        - Return valid JSON matching the requested schema.

        COMPLETION
        - Return finishReason = COMPLETED when finalResponse is available.
        - Return finishReason = ERROR only when the task cannot be completed.
        """;
```

What this code is doing:

- it defines the stable identity of the Market Data Agent
- it tells the model what this agent is responsible for
- it tells the model what rules it must follow
- it tells the model what a completed result should look like
- it keeps those instructions out of the runtime prompt

### Step 2B: replace the bean body

In the same file, find this method:

```java
@Bean("marketDataChatClient")
public ChatClient marketDataChatClient(ChatModel chatModel, MarketDataTools marketDataTools) {
    // PART 2 STEP 2:
    // Replace the return statement below with the snippet from the Part 2 guide.
    return ChatClient.builder(chatModel)
            .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
            .build();
}
```

Replace the method body with this exact code:

```java
return ChatClient.builder(chatModel)
        .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
        .defaultTools(marketDataTools)
        .defaultSystem(DEFAULT_PROMPT)
        .build();
```

Why you did this:

- the config class defines the stable setup of the agent
- the default prompt defines role and rules
- the tool becomes available to the model on every request
- structured output lets Spring AI map the response into `MarketDataResult`

What this code is doing:

- it creates a dedicated client for the Market Data Agent
- it combines the agent's stable instructions, tool access, and structured-output behavior in one place
- it ensures every market-data request runs with the same setup

## Step 3: Implement `buildPrompt(...)`

Open:

`src/main/java/com/redis/stockanalysisagent/agent/marketdataagent/MarketDataAgent.java`

Find this method:

```java
private String buildPrompt(String ticker, String question) {
    // PART 2 STEP 3:
    // Replace this method body with the snippet from the Part 2 guide.
    throw new UnsupportedOperationException("Part 2: implement buildPrompt(...)");
}
```

Replace the method body with this exact code:

```java
return """
        TICKER
        %s

        USER_QUESTION
        %s

        INSTRUCTIONS
        - Use the available tool to fetch a current market snapshot for the ticker.
        - Populate finalResponse with the exact tool values.
        - message should directly answer the user's question in one concise sentence.
        """.formatted(ticker.toUpperCase(), question);
```

Why you did this:

- the default prompt stays stable
- the runtime prompt changes on every request
- this is where you inject the ticker and current user question

What this code is doing:

- it packages the current request into the agent's runtime prompt
- it gives the agent the specific ticker and question for this execution
- it reminds the agent to use its tool and return a structured result

## Step 4: Implement `execute(...)`

In the same file, find this method:

```java
public MarketDataResult execute(String ticker, String question) {
    // PART 2 STEP 4:
    // Replace this method body with the snippet from the Part 2 guide.
    throw new UnsupportedOperationException("Part 2: implement execute(...)");
}
```

Replace the method body with this exact code:

```java
ResponseEntity<ChatResponse, MarketDataResult> response = marketDataChatClient
        .prompt()
        .user(buildPrompt(ticker, question))
        .call()
        .responseEntity(MarketDataResult.class);

TokenUsageSummary tokenUsage = TokenUsageSummary.from(response.response());
MarketDataResult entity = response.entity();

if (entity == null || entity.getFinalResponse() == null || entity.getFinishReason() != MarketDataResult.FinishReason.COMPLETED) {
    throw new IllegalStateException("Market Data Agent returned an invalid response.");
}

entity.setTokenUsage(tokenUsage);
return entity;
```

Why you did this:

- this is the full execution flow of the agent
- Spring AI calls the model
- the model can call the tool
- Spring AI maps the structured output into `MarketDataResult`
- your application now has a typed result it can pass into orchestration

What this code is doing:

- it runs the Market Data Agent with the configured client and runtime prompt
- it asks Spring AI to return a typed `MarketDataResult` instead of raw text
- it validates that the agent produced a usable completed result
- it returns a structured output that orchestration can safely consume later

## What You Just Built

You have now implemented the full specialist-agent pattern:

- a tool
- a `ChatClient` config class
- a runtime prompt
- an `execute()` method that returns structured output

## What “Done” Looks Like

You are done when you understand this flow:

1. `buildPrompt(...)` creates the runtime request
2. `ChatClient` sends the request to the model
3. the model can call `getMarketSnapshot(...)`
4. Spring AI executes the tool
5. Spring AI maps the response into `MarketDataResult`
6. the application can use that result in a larger workflow
