# Stock Analysis Agent Workshop Plan

This module is the learner-facing workshop track.

The finalized reference implementation lives in
[stockanalysisagent](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent).

The workshop is split into three parts:

1. [Part 1: Orchestration](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/docs/part-1-orchestration.md)
2. [Part 2: Redis Agent Memory](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/docs/part-2-redis-agent-memory.md)
3. [Part 3: Regular Caching and Semantic Caching](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/docs/part-3-caching.md)

## Guiding Principles

- Keep the workshop code smaller than the finalized implementation.
- Teach one architectural idea at a time.
- Pre-provide infrastructure and boilerplate that does not help learners understand agent design.
- Ask learners to implement the parts that reveal how Spring AI agents are structured and coordinated.
- Use mock data first. Add Redis and cache infrastructure only in later parts.

## Skeleton Strategy

The workshop module should begin as a Part 1 skeleton.

That means:

- include only the directories and files needed for Part 1
- add Redis and memory files only when Part 2 starts
- add cache and semantic-cache files only when Part 3 starts

Do not preload the full final architecture into the workshop skeleton.

## Suggested File Ownership

### Pre-Provided

- application bootstrap
- Gradle setup
- frontend shell
- controller shell
- mock services
- tool scaffolding
- package layout
- simple sample data

### Learner-Implemented

- prompts
- `ChatClient` config classes
- agent service execution methods
- result objects and contracts
- orchestration logic
- memory advisor logic in Part 2
- cache integration logic in Part 3
