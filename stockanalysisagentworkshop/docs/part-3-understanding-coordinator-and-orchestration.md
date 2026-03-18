# Part 3: Understanding Coordinator and Orchestration

Before you implement the orchestration layer, you need one more mental model.

At this point, you already know how to build a specialist agent.

What you have not fully built yet is the layer that decides:

- which agents should run
- what they should receive
- in what order they should run
- how their outputs become one final answer

That is what this part is about.

## The Core Question

If the specialist agents already exist, what else is missing?

Three things are still needed:

1. something that routes the user request
2. something that turns that route into application objects
3. something that actually runs the selected agents

In this workshop, those responsibilities are split across:

- `CoordinatorRoutingAgentConfig`
- `CoordinatorRoutingAgent`
- `CoordinatorAgent`
- `AgentOrchestrationService`

## Why Not Put Everything Into One Class?

Because these classes do different jobs.

If we collapse them into one large class, it becomes much harder to understand:

- which part is model setup
- which part is model execution
- which part is application logic
- which part is workflow execution

This split keeps the responsibilities clear.

## 1. `CoordinatorRoutingAgentConfig`

This class defines how the router talks to the model.

Its responsibility is:

- configure the router `ChatClient`
- define the router's default system prompt
- attach the advisors the router needs
- enable structured output

This is the same kind of job that a specialist agent config class already does.

The difference is that the router does not fetch market data or fundamentals.

It reasons about what should happen next.

So this class answers:

"How should the routing agent talk to the model?"

Not:

"What should the application do with the result?"

## 2. `CoordinatorRoutingAgent`

This class executes the routing call.

Its responsibility is:

- send the user request to the router `ChatClient`
- pass the conversation ID
- map the response into `RoutingDecision`
- return token usage together with that decision

So this class answers:

"How do we execute the router and get back a structured routing result?"

It is still very close to the Spring AI layer.

## 3. `RoutingDecision`

`RoutingDecision` is the router's structured output.

It is the contract between the model and the application.

It contains things like:

- `finishReason`
- `resolvedTicker`
- `resolvedQuestion`
- `selectedAgents`
- `nextPrompt`
- `reasoning`

This is important because the router does not directly run anything.

It returns a structured decision that the application can inspect.

That is how we keep orchestration in code instead of hiding it inside the model.

## 4. `CoordinatorAgent`

This class sits one level above the router.

Its responsibility is to take the raw routing output and turn it into application-friendly objects.

It does three important things:

### `execute(...)`

Calls the router and returns the routing result.

### `createPlan(...)`

Turns the selected agents into an `ExecutionPlan`.

That plan is what the orchestrator will use.

### `toAnalysisRequest(...)`

Turns the routing decision into the normalized request object that the specialist agents will use.

So this class answers:

"How do we convert the router's structured output into application flow?"

This is why the coordinator is not the same thing as the router.

The router talks to the model.

The coordinator turns the model's decision into application objects.

## 5. `ExecutionPlan`

`ExecutionPlan` is a very small but very important object.

It says:

- which agents were selected
- what routing reasoning led to that decision

That plan is what the orchestrator consumes.

So the flow is:

- router returns `RoutingDecision`
- coordinator builds `ExecutionPlan`
- orchestrator runs the plan

## 6. `AnalysisRequest`

`AnalysisRequest` is another small but important object.

It contains the normalized values the specialist agents need, such as:

- ticker
- question

Why do we want this?

Because we do not want every specialist agent to read directly from the raw routing output.

We want one clean request object that the orchestrator can pass through the system.

## 7. `AgentOrchestrationService`

This is the class that actually runs the workflow.

Its responsibility is:

- read the `ExecutionPlan`
- run the selected specialist agents
- keep track of their outputs
- pass those outputs to synthesis
- return the final `AnalysisResponse`

This is the class that answers:

"Now that we know what should run, how do we actually run it?"

That is why it is called orchestration.

## The Full Flow

Here is the full chain:

```text
User request
    |
    v
CoordinatorRoutingAgent
    |
    v
RoutingDecision
    |
    v
CoordinatorAgent
    |
    +--> AnalysisRequest
    |
    +--> ExecutionPlan
            |
            v
    AgentOrchestrationService
            |
            v
    Specialist agents
            |
            v
    Synthesis agent
            |
            v
       Final answer
```

## Why This Split Matters

This architecture gives us a few advantages:

- the routing prompt stays separate from orchestration logic
- the application decides how the plan is executed
- the specialist agents stay focused on their own work
- the system is easier to debug because each stage has a clear output

Most importantly, this split keeps the design explicit.

The model suggests.

The application decides.

## What You Should Understand Before Part 4

Before moving on, make sure these ideas feel clear:

- the router is the model-facing decision step
- `RoutingDecision` is the router's structured output
- the coordinator turns that decision into application objects
- `ExecutionPlan` tells the orchestrator what to run
- `AnalysisRequest` gives the specialist agents normalized input
- the orchestrator runs the workflow in code

If those ideas feel natural, you are ready to implement the orchestration layer itself.
