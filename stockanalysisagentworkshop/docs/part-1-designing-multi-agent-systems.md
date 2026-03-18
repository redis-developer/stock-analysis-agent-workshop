# Part 1: Designing Multi-Agent Systems

Now that we have a basic Spring AI mental model, we can move into the real question of this workshop:

How do we design a multi-agent system that is easy to understand, easy to extend, and explicit about what each agent is responsible for?

This part is about design first.

Before we write code, we want to understand:

- why people moved from single-agent systems to multi-agent systems
- when a multi-agent design is actually useful
- how to break one problem into multiple specialized agents
- how Spring AI fits into that design
- how the agents in this workshop will be orchestrated

## 1. Single-Agent vs Multi-Agent Systems

A single-agent system gives one model one large responsibility.

For example:

- understand the user request
- decide what data is needed
- call tools
- interpret the data
- combine everything into a final answer

That can work well for simple tasks.

But as soon as the problem becomes larger, one agent starts carrying too much:

- too much prompt context
- too many responsibilities
- too many tools
- too many failure modes

A multi-agent system breaks that work into smaller agents with narrower responsibilities.

Instead of one agent doing everything, we create specialized agents such as:

- a routing agent
- a market-data agent
- a fundamentals agent
- a news agent
- a technical-analysis agent
- a synthesis agent

Each one gets:

- a smaller prompt
- a clearer purpose
- a smaller tool set
- a more focused output

That usually makes the system easier to reason about.

## 2. Why People Started Building Multi-Agent Systems

Early AI applications often tried to solve everything in one shot.

That worked for small prompts, but it started breaking down when tasks became more realistic.

Teams ran into the same problems again and again:

- prompts became too large
- tools became too broad
- outputs became harder to trust
- reasoning became less predictable
- changing one part of the system affected everything else

So people started splitting work into smaller units.

This shift was not mainly about making the system feel more "agentic." It was about making the system more maintainable.

Multi-agent systems became useful because they let us separate different kinds of work:

- routing
- data collection
- interpretation
- synthesis

That separation gives us a few important benefits:

- each agent is easier to prompt
- each agent is easier to test
- each agent can have its own tools
- each handoff can be made explicit
- orchestration becomes normal application code instead of hidden model behavior

That last point matters a lot. In this workshop, we do not want the model secretly deciding the entire application flow. We want the application to own the flow.

## 3. Design First, Then Implement

The most common mistake with multi-agent systems is starting with code.

The better order is:

1. define the overall problem
2. break it into responsibilities
3. define the contracts between agents
4. decide how the orchestration will work
5. only then write the Spring AI classes

So before we ask, "What classes should we create?", we should ask:

- what job does each agent have?
- what data does it need?
- what tools should it have?
- what should it return?
- which agent runs next?

If we skip that design step, the code usually turns into one large pile of prompts and tools again, even if we call it multi-agent.

## 4. Workflow vs Orchestration

These two ideas are related, but they are not the same.

### Workflow

A workflow is the sequence of work we want to happen.

For example:

1. understand the user request
2. collect market data
3. collect fundamentals
4. collect news
5. collect technical signals
6. synthesize a final answer

That is the workflow.

It describes the stages.

### Orchestration

Orchestration is the application code that decides how that workflow actually runs.

It answers questions like:

- which agents should run for this request?
- in what order?
- what outputs should be passed forward?
- when should we stop?
- when should we ask for more input?

So a simple shortcut is:

- workflow = the path
- orchestration = the code that drives the path

In Spring AI, this distinction matters because Spring AI gives us building blocks for individual agents, but it does not orchestrate a multi-agent system for us.

That part is our job.

## 5. The Multi-Agent System We Are Building

In this workshop, learners will build a stock-analysis assistant.

The user asks a question such as:

- "Should I look at Apple or Microsoft right now?"
- "Give me a quick analysis of Nvidia."
- "What matters most if I want a bullish view on Amazon?"

The system will break that request into specialized steps.

### Agents in our system

#### Coordinator Agent

Its job is to:

- understand the user request
- decide whether the request is clear enough
- choose which specialist agents should run
- produce an execution plan

This agent does not produce the final stock-analysis answer.

It decides how the rest of the system should run.

#### Market Data Agent

Its job is to:

- fetch current market price context
- return a structured market snapshot

#### Fundamentals Agent

Its job is to:

- fetch business and financial information
- return a structured fundamentals snapshot

#### News Agent

Its job is to:

- collect recent company and market news
- return a structured news snapshot

#### Technical Analysis Agent

Its job is to:

- compute technical indicators and signals
- return a structured technical-analysis snapshot

#### Synthesis Agent

Its job is to:

- read the outputs of the specialist agents
- combine them into one final answer for the user

### High-level flow

```text
User Request
    |
    v
Coordinator Agent
    |
    v
Routing Decision / Execution Plan
    |
    v
Agent Orchestration Service
    |
    +--> Market Data Agent -----------+
    |                                 |
    +--> Fundamentals Agent ----------|
    |                                 |
    +--> News Agent ------------------|--> Synthesis Agent --> Final Answer
    |                                 |
    +--> Technical Analysis Agent ----+

Optional context:
Market Data Agent --> Fundamentals Agent
```

This is a good multi-agent design because each agent has one clear responsibility.

The important detail is that the Coordinator Agent does not directly execute the specialist agents.

Instead:

- the Coordinator Agent decides what should run
- the orchestration service runs the selected agents
- the synthesis agent turns the structured outputs into the final answer

## 6. How Do We Break a Problem Into Agents?

There is no perfect universal formula, but there is a very useful way to think about it.

Create a separate agent when the work has a different:

- purpose
- prompt
- tool set
- output shape

For our stock-analysis system, the boundaries are natural:

- market data is different from fundamentals
- fundamentals are different from news
- news is different from technical analysis
- synthesis is different from raw data collection

That gives us a system where each agent stays focused.

## 7. The Most Important Design Rule: Use Contracts

Agents should not pass raw prompts or giant blobs of text to each other.

Instead, each agent should return structured data.

That is one of the most important design choices in this workshop.

For example:

- the Market Data Agent returns a `MarketSnapshot`
- the Fundamentals Agent returns a `FundamentalsSnapshot`
- the News Agent returns a `NewsSnapshot`
- the Technical Analysis Agent returns a `TechnicalAnalysisSnapshot`

In Spring AI terms, this usually means we want structured output, not just plain text.

That way an agent can return a typed object that the application can trust and pass forward.

Why this matters:

- downstream agents get only the context they need
- the handoffs become explicit
- the system is easier to debug
- the system is easier to evolve

This is also why the synthesis step is separate. It receives structured outputs, not entire prior prompts.

## 8. How We Build Agents in Spring AI

For this workshop, each agent will follow a simple pattern:

- one configuration class
- one execution class
- one structured result type

### Why split config from execution?

Because these are different responsibilities.

The configuration class defines:

- which `ChatModel` the agent uses
- the default system prompt
- the default tools
- the default advisors

The execution class defines:

- when the agent is called
- what runtime prompt it sends
- how the response is handled
- how the structured response is mapped back into Java

That separation makes the code easier to understand.

It also makes the agent easier to reuse from different entry points later.

### Example: configuration class

```java
@Configuration
public class MarketDataAgentConfig {

    private static final String DEFAULT_PROMPT = """
            ROLE
            You are the Market Data Agent.

            RESPONSIBILITY
            Use the available tool to fetch current market data.

            RULES
            - Always use the tool.
            - Never invent values.
            - Return valid JSON matching the schema.
            """;

    @Bean("marketDataChatClient")
    ChatClient marketDataChatClient(ChatModel chatModel, MarketDataTools marketDataTools) {
        return ChatClient.builder(chatModel)
                .defaultSystem(DEFAULT_PROMPT)
                .defaultTools(marketDataTools)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .build();
    }
}
```

### Example: execution class

```java
@Service
public class MarketDataAgent {

    private final ChatClient marketDataChatClient;

    public MarketDataAgent(@Qualifier("marketDataChatClient") ChatClient marketDataChatClient) {
        this.marketDataChatClient = marketDataChatClient;
    }

    public MarketDataResult execute(String ticker, String question) {
        return marketDataChatClient.prompt()
                .user(buildPrompt(ticker, question))
                .call()
                .responseEntity(MarketDataResult.class)
                .entity();
    }

    private String buildPrompt(String ticker, String question) {
        return """
                TICKER
                %s

                USER_QUESTION
                %s
                """.formatted(ticker.toUpperCase(), question);
    }
}
```

This split is important because it keeps the agent architecture consistent across the whole system.

It also makes the control flow explicit:

- the prompt asks for a structured result
- Spring AI maps that result into a Java type
- the application decides what to do next

## 9. What Goes Into the Default Prompt?

The default prompt is where we define the stable identity of the agent.

That usually includes:

- role
- responsibility
- rules
- completion expectations

For example, a strong default prompt often answers these questions:

- who are you?
- what is your job?
- what must you always do?
- what must you never do?
- what kind of result should you return?

This is a good place for stable instructions such as:

- "Always use the tool before returning a result."
- "Never invent prices."
- "Return valid JSON matching the schema."

What does not belong there:

- the current ticker
- the current user question
- the request-specific context

Those belong in the runtime prompt sent by the execution class.

So the split is:

- default prompt = stable identity and rules
- runtime prompt = request-specific input

When an agent returns structured output, the default prompt usually also reminds the model to follow the expected schema.

## 10. How the Architecture of Our System Fits Together

At a high level, our system has two layers:

### Layer 1: specialized agents

These are the agents that do focused work:

- market data
- fundamentals
- news
- technical analysis
- synthesis

### Layer 2: orchestration

This is the application code that coordinates them:

- the Coordinator Agent decides what should run
- the orchestration service runs the selected agents
- the orchestration service passes only the needed outputs forward

That means the agents do not orchestrate each other.

They remain focused on their own responsibilities.

This is one of the biggest design wins in the whole workshop.

## 11. How the Sub-Agents Will Be Orchestrated

Our orchestration will be explicit and application-driven.

That means:

1. the Coordinator Agent reads the user request
2. the Coordinator Agent returns a structured routing decision
3. the application turns that routing decision into an execution plan
4. the orchestration service runs the selected specialist agents
5. the synthesis agent creates the final answer

Conceptually, it looks like this:

```java
RoutingDecision decision = coordinatorAgent.execute(userMessage, conversationId);

ExecutionPlan plan = coordinatorAgent.createPlan(decision);
AnalysisRequest request = coordinatorAgent.toAnalysisRequest(decision);

MarketSnapshot market = marketDataAgent.execute(...).getFinalResponse();
FundamentalsSnapshot fundamentals = fundamentalsAgent.execute(...).getFinalResponse();
NewsSnapshot news = newsAgent.execute(...).getFinalResponse();
TechnicalAnalysisSnapshot technical = technicalAnalysisAgent.execute(...).getFinalResponse();

String finalAnswer = synthesisAgent.synthesize(
        request,
        market,
        fundamentals,
        news,
        technical
).finalAnswer();
```

The important part here is not the exact syntax.

The important part is that orchestration is explicit.

The application, not the model, controls:

- what runs
- what order it runs in
- what data gets passed forward

The model proposes structured decisions and structured results, but the application owns the orchestration.

## 12. One More Important Idea: Minimal Context Passing

When one agent finishes, we should not dump all of its raw output into the next agent.

Instead, we should pass only what the next step actually needs.

For example:

- the Fundamentals Agent may use market price context
- the Synthesis Agent should receive structured snapshots
- the coordinator should not forward the entire conversation history to every specialist

This matters because smaller context usually means:

- lower token usage
- less prompt noise
- clearer responsibilities

This is one of the main practical reasons multi-agent systems work better than giant single-agent prompts.

## 13. What Else Matters in a Good Multi-Agent Design?

Yes, there are a few more things worth keeping in mind.

### Keep each agent narrow

If one agent starts needing every tool and every prompt rule, it is probably too broad.

### Make the handoffs structured

Prefer typed outputs over free-form text between agents.

### Keep orchestration in code

Do not hide the application flow inside the model.

### Optimize for clarity first

Before you optimize for memory, caching, or advanced routing, make sure the base system is understandable.

That is exactly why this workshop starts with orchestration first.

## 14. What Learners Should Understand After Part 1

By the end of this part, learners should be able to explain:

- why a multi-agent system is different from a single-agent system
- why we design the system before coding it
- why workflow and orchestration are not the same thing
- why each agent gets its own configuration and execution class
- why prompts, tools, and outputs should stay focused
- why orchestration belongs in application code

If those ideas are clear, then the implementation work becomes much more straightforward.
