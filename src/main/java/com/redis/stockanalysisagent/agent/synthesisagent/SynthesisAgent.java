package com.redis.stockanalysisagent.agent.synthesisagent;

import com.redis.stockanalysisagent.agent.AgentExecution;
import com.redis.stockanalysisagent.agent.AgentExecutionStatus;
import com.redis.stockanalysisagent.agent.AgentType;
import com.redis.stockanalysisagent.agent.coordinatoragent.ExecutionPlan;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.agent.newsagent.NewsItem;
import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;
import com.redis.stockanalysisagent.api.AnalysisRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class SynthesisAgent {

    private static final Logger log = LoggerFactory.getLogger(SynthesisAgent.class);

    private static final String DEFAULT_PROMPT = """
            ROLE
            You are the Synthesis Agent for a stock-analysis system.

            RESPONSIBILITY
            Combine the structured outputs from specialized agents into one grounded answer.

            RULES
            - Use only the information provided in the prompt.
            - Do not invent prices, metrics, headlines, or technical signals.
            - Mention when signals are mixed or incomplete.
            - Be concise and practical for an investor who asked the question.
            - Do not mention internal agent names unless it helps clarify uncertainty.

            OUTPUT
            Return valid JSON matching the requested schema.
            The finalAnswer should be a concise paragraph or two.
            """;

    private final ChatClient synthesisChatClient;

    public SynthesisAgent(Optional<ChatModel> chatModel) {
        if (chatModel.isEmpty()) {
            this.synthesisChatClient = null;
            return;
        }

        this.synthesisChatClient = ChatClient.builder(chatModel.orElseThrow())
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultSystem(DEFAULT_PROMPT)
                .build();
    }

    public String synthesize(
            AnalysisRequest request,
            ExecutionPlan executionPlan,
            MarketSnapshot marketSnapshot,
            FundamentalsSnapshot fundamentalsSnapshot,
            NewsSnapshot newsSnapshot,
            TechnicalAnalysisSnapshot technicalAnalysisSnapshot,
            List<AgentExecution> agentExecutions
    ) {
        long pendingAgents = agentExecutions.stream()
                .filter(execution -> execution.status() != AgentExecutionStatus.COMPLETED)
                .count();

        if (synthesisChatClient == null) {
            return fallbackAnswer(
                    request,
                    executionPlan,
                    marketSnapshot,
                    fundamentalsSnapshot,
                    newsSnapshot,
                    technicalAnalysisSnapshot,
                    agentExecutions,
                    pendingAgents
            );
        }

        try {
            ResponseEntity<ChatResponse, SynthesisResponse> response = synthesisChatClient
                    .prompt()
                    .user(buildPrompt(request, executionPlan, marketSnapshot, fundamentalsSnapshot, newsSnapshot, technicalAnalysisSnapshot))
                    .call()
                    .responseEntity(SynthesisResponse.class);

            SynthesisResponse entity = response.entity();
            if (entity == null || entity.finalAnswer() == null || entity.finalAnswer().isBlank()) {
                return fallbackAnswer(
                        request,
                        executionPlan,
                        marketSnapshot,
                        fundamentalsSnapshot,
                        newsSnapshot,
                        technicalAnalysisSnapshot,
                        agentExecutions,
                        pendingAgents
                );
            }

            return appendPendingContext(entity.finalAnswer().trim(), agentExecutions, pendingAgents);
        } catch (RuntimeException ex) {
            log.warn("Falling back to deterministic synthesis because the model-backed synthesis failed.", ex);
            return fallbackAnswer(
                    request,
                    executionPlan,
                    marketSnapshot,
                    fundamentalsSnapshot,
                    newsSnapshot,
                    technicalAnalysisSnapshot,
                    agentExecutions,
                    pendingAgents
            );
        }
    }

    private String buildPrompt(
            AnalysisRequest request,
            ExecutionPlan executionPlan,
            MarketSnapshot marketSnapshot,
            FundamentalsSnapshot fundamentalsSnapshot,
            NewsSnapshot newsSnapshot,
            TechnicalAnalysisSnapshot technicalAnalysisSnapshot
    ) {
        return """
                QUESTION
                %s

                REQUESTED_TICKER
                %s

                ROUTING_CONTEXT
                Selected agents: %s
                Requires synthesis: %s
                Routing reasoning: %s

                MARKET_DATA
                %s

                FUNDAMENTALS
                %s

                NEWS
                %s

                TECHNICAL_ANALYSIS
                %s

                INSTRUCTIONS
                - Answer the user directly.
                - Weigh the available signals and call out conflicts or uncertainty.
                - Keep the response grounded in these inputs only.
                """.formatted(
                request.question(),
                request.ticker(),
                executionPlan.selectedAgents(),
                executionPlan.requiresSynthesis(),
                executionPlan.routingReasoning(),
                formatMarketSection(marketSnapshot),
                formatFundamentalsSection(fundamentalsSnapshot),
                formatNewsSection(newsSnapshot),
                formatTechnicalSection(technicalAnalysisSnapshot)
        );
    }

    private String fallbackAnswer(
            AnalysisRequest request,
            ExecutionPlan executionPlan,
            MarketSnapshot marketSnapshot,
            FundamentalsSnapshot fundamentalsSnapshot,
            NewsSnapshot newsSnapshot,
            TechnicalAnalysisSnapshot technicalAnalysisSnapshot,
            List<AgentExecution> agentExecutions,
            long pendingAgents
    ) {
        String baseAnswer = buildFallbackBaseAnswer(
                request,
                executionPlan,
                marketSnapshot,
                fundamentalsSnapshot,
                newsSnapshot,
                technicalAnalysisSnapshot
        );

        if (pendingAgents == 0) {
            return baseAnswer;
        }

        return appendPendingContext(baseAnswer, agentExecutions, pendingAgents);
    }

    private String appendPendingContext(String baseAnswer, List<AgentExecution> agentExecutions, long pendingAgents) {
        if (pendingAgents == 0) {
            return baseAnswer;
        }

        return baseAnswer + " Additional context from %s is planned but not implemented in this slice yet."
                .formatted(
                        agentExecutions.stream()
                                .filter(execution -> execution.status() != AgentExecutionStatus.COMPLETED)
                                .map(AgentExecution::agentType)
                                .filter(agentType -> agentType != AgentType.SYNTHESIS)
                                .toList()
                );
    }

    private String buildFallbackBaseAnswer(
            AnalysisRequest request,
            ExecutionPlan executionPlan,
            MarketSnapshot marketSnapshot,
            FundamentalsSnapshot fundamentalsSnapshot,
            NewsSnapshot newsSnapshot,
            TechnicalAnalysisSnapshot technicalAnalysisSnapshot
    ) {
        if (marketSnapshot != null
                && fundamentalsSnapshot != null
                && newsSnapshot != null
                && hasNews(newsSnapshot)
                && technicalAnalysisSnapshot != null) {
            return """
                    Based on the currently implemented agents, %s is trading at $%s (%s%% vs. previous close).
                    %s reported revenue of %s with revenue growth of %s and net margin of %s.
                    Recent news signals include %s.
                    Technical signals are %s with %s momentum and RSI(14) at %s.
                    The coordinator selected %s for the question "%s".
                    """.formatted(
                    marketSnapshot.symbol(),
                    marketSnapshot.currentPrice(),
                    marketSnapshot.percentChange(),
                    fundamentalsSnapshot.companyName(),
                    formatMoney(fundamentalsSnapshot.revenue()),
                    formatPercent(fundamentalsSnapshot.revenueGrowthPercent()),
                    formatPercent(fundamentalsSnapshot.netMarginPercent()),
                    newsHighlight(newsSnapshot),
                    technicalAnalysisSnapshot.trendSignal().toLowerCase(),
                    technicalAnalysisSnapshot.momentumSignal().toLowerCase(),
                    technicalAnalysisSnapshot.rsi14(),
                    executionPlan.selectedAgents(),
                    request.question()
            ).replace('\n', ' ').trim();
        }

        if (marketSnapshot != null && technicalAnalysisSnapshot != null) {
            return """
                    Based on the currently implemented agents, %s is trading at $%s (%s%% vs. previous close).
                    Technical signals are %s with %s momentum and RSI(14) at %s.
                    The coordinator selected %s for the question "%s".
                    """.formatted(
                    marketSnapshot.symbol(),
                    marketSnapshot.currentPrice(),
                    marketSnapshot.percentChange(),
                    technicalAnalysisSnapshot.trendSignal().toLowerCase(),
                    technicalAnalysisSnapshot.momentumSignal().toLowerCase(),
                    technicalAnalysisSnapshot.rsi14(),
                    executionPlan.selectedAgents(),
                    request.question()
            ).replace('\n', ' ').trim();
        }

        if (marketSnapshot != null && fundamentalsSnapshot != null) {
            return """
                    Based on the currently implemented agents, %s is trading at $%s (%s%% vs. previous close).
                    %s reported revenue of %s with revenue growth of %s and a net margin of %s.
                    The coordinator selected %s for the question "%s".
                    """.formatted(
                    marketSnapshot.symbol(),
                    marketSnapshot.currentPrice(),
                    marketSnapshot.percentChange(),
                    fundamentalsSnapshot.companyName(),
                    formatMoney(fundamentalsSnapshot.revenue()),
                    formatPercent(fundamentalsSnapshot.revenueGrowthPercent()),
                    formatPercent(fundamentalsSnapshot.netMarginPercent()),
                    executionPlan.selectedAgents(),
                    request.question()
            ).replace('\n', ' ').trim();
        }

        if (marketSnapshot != null) {
            return """
                    Based on the currently implemented agents, %s is trading at $%s (%s%% vs. previous close).
                    The coordinator selected %s for the question "%s".
                    """.formatted(
                    marketSnapshot.symbol(),
                    marketSnapshot.currentPrice(),
                    marketSnapshot.percentChange(),
                    executionPlan.selectedAgents(),
                    request.question()
            ).replace('\n', ' ').trim();
        }

        if (fundamentalsSnapshot != null) {
            return """
                    %s reported revenue of %s and net income of %s.
                    Revenue growth was %s and net margin was %s.
                    The coordinator selected %s for the question "%s".
                    """.formatted(
                    fundamentalsSnapshot.companyName(),
                    formatMoney(fundamentalsSnapshot.revenue()),
                    formatMoney(fundamentalsSnapshot.netIncome()),
                    formatPercent(fundamentalsSnapshot.revenueGrowthPercent()),
                    formatPercent(fundamentalsSnapshot.netMarginPercent()),
                    executionPlan.selectedAgents(),
                    request.question()
            ).replace('\n', ' ').trim();
        }

        if (newsSnapshot != null && hasNews(newsSnapshot)) {
            return """
                    Recent news signals for %s include %s.
                    The coordinator selected %s for the question "%s".
                    """.formatted(
                    newsSnapshot.companyName(),
                    newsHighlight(newsSnapshot),
                    executionPlan.selectedAgents(),
                    request.question()
            ).replace('\n', ' ').trim();
        }

        if (technicalAnalysisSnapshot != null) {
            return """
                    Technical signals for %s are %s with %s momentum.
                    RSI(14) is %s, with the latest close at $%s versus SMA(20) $%s and EMA(20) $%s.
                    The coordinator selected %s for the question "%s".
                    """.formatted(
                    technicalAnalysisSnapshot.ticker(),
                    technicalAnalysisSnapshot.trendSignal().toLowerCase(),
                    technicalAnalysisSnapshot.momentumSignal().toLowerCase(),
                    technicalAnalysisSnapshot.rsi14(),
                    technicalAnalysisSnapshot.latestClose(),
                    technicalAnalysisSnapshot.sma20(),
                    technicalAnalysisSnapshot.ema20(),
                    executionPlan.selectedAgents(),
                    request.question()
            ).replace('\n', ' ').trim();
        }

        return """
                The coordinator selected %s for the question "%s".
                The required specialized analysis is planned, but the selected agents are not implemented in this slice yet.
                """.formatted(
                executionPlan.selectedAgents(),
                request.question()
        ).replace('\n', ' ').trim();
    }

    private String formatMarketSection(MarketSnapshot marketSnapshot) {
        if (marketSnapshot == null) {
            return "No market data available.";
        }

        return """
                symbol: %s
                currentPrice: %s
                previousClose: %s
                percentChange: %s
                asOf: %s
                source: %s
                """.formatted(
                marketSnapshot.symbol(),
                marketSnapshot.currentPrice(),
                marketSnapshot.previousClose(),
                marketSnapshot.percentChange(),
                marketSnapshot.asOf(),
                marketSnapshot.source()
        ).trim();
    }

    private String formatFundamentalsSection(FundamentalsSnapshot fundamentalsSnapshot) {
        if (fundamentalsSnapshot == null) {
            return "No fundamentals data available.";
        }

        return """
                companyName: %s
                revenue: %s
                revenueGrowthPercent: %s
                netIncome: %s
                operatingMarginPercent: %s
                netMarginPercent: %s
                priceToSales: %s
                priceToEarnings: %s
                source: %s
                """.formatted(
                fundamentalsSnapshot.companyName(),
                fundamentalsSnapshot.revenue(),
                fundamentalsSnapshot.revenueGrowthPercent(),
                fundamentalsSnapshot.netIncome(),
                fundamentalsSnapshot.operatingMarginPercent(),
                fundamentalsSnapshot.netMarginPercent(),
                fundamentalsSnapshot.priceToSales(),
                fundamentalsSnapshot.priceToEarnings(),
                fundamentalsSnapshot.source()
        ).trim();
    }

    private String formatNewsSection(NewsSnapshot newsSnapshot) {
        if (newsSnapshot == null || (!hasNews(newsSnapshot) && (newsSnapshot.webSummary() == null || newsSnapshot.webSummary().isBlank()))) {
            return "No news data available.";
        }

        StringBuilder builder = new StringBuilder();
        if (newsSnapshot.webSummary() != null && !newsSnapshot.webSummary().isBlank()) {
            builder.append("webSummary: ").append(newsSnapshot.webSummary()).append(System.lineSeparator());
        }
        if (!newsSnapshot.officialItems().isEmpty()) {
            builder.append("officialSignals: ")
                    .append(formatNewsItems(newsSnapshot.officialItems()))
                    .append(System.lineSeparator());
        }
        if (!newsSnapshot.webItems().isEmpty()) {
            builder.append("webNews: ")
                    .append(formatNewsItems(newsSnapshot.webItems()))
                    .append(System.lineSeparator());
        }
        builder.append("source: ").append(newsSnapshot.source());
        return builder.toString().trim();
    }

    private String formatTechnicalSection(TechnicalAnalysisSnapshot technicalAnalysisSnapshot) {
        if (technicalAnalysisSnapshot == null) {
            return "No technical-analysis data available.";
        }

        return """
                interval: %s
                latestClose: %s
                sma20: %s
                ema20: %s
                rsi14: %s
                trendSignal: %s
                momentumSignal: %s
                source: %s
                """.formatted(
                technicalAnalysisSnapshot.interval(),
                technicalAnalysisSnapshot.latestClose(),
                technicalAnalysisSnapshot.sma20(),
                technicalAnalysisSnapshot.ema20(),
                technicalAnalysisSnapshot.rsi14(),
                technicalAnalysisSnapshot.trendSignal(),
                technicalAnalysisSnapshot.momentumSignal(),
                technicalAnalysisSnapshot.source()
        ).trim();
    }

    private String formatNewsItems(List<NewsItem> items) {
        return items.stream()
                .limit(3)
                .map(this::formatNewsItem)
                .toList()
                .toString();
    }

    private String formatNewsItem(NewsItem item) {
        return "{publisher=%s, label=%s, date=%s, title=%s}".formatted(
                item.publisher(),
                item.label(),
                formatLocalDate(item.publishedAt()),
                item.title()
        );
    }

    private String formatLocalDate(LocalDate value) {
        return value != null ? value.toString() : "unavailable";
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return "unavailable";
        }

        BigDecimal billions = value.divide(BigDecimal.valueOf(1_000_000_000L), 2, RoundingMode.HALF_UP);
        return "$" + billions + "B";
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) {
            return "unavailable";
        }

        return value.setScale(2, RoundingMode.HALF_UP) + "%";
    }

    private boolean hasNews(NewsSnapshot newsSnapshot) {
        return !newsSnapshot.webItems().isEmpty() || !newsSnapshot.officialItems().isEmpty();
    }

    private String newsHighlight(NewsSnapshot newsSnapshot) {
        if (!newsSnapshot.webItems().isEmpty()) {
            String firstTitle = newsSnapshot.webItems().getFirst().title();
            String firstPublisher = newsSnapshot.webItems().getFirst().publisher();
            if (!newsSnapshot.officialItems().isEmpty()) {
                return "%s (%s), alongside official SEC signals such as %s on %s".formatted(
                        firstTitle,
                        firstPublisher,
                        newsSnapshot.officialItems().getFirst().label(),
                        newsSnapshot.officialItems().getFirst().publishedAt()
                );
            }

            return "%s (%s)".formatted(firstTitle, firstPublisher);
        }

        return "%s on %s".formatted(
                newsSnapshot.officialItems().getFirst().label(),
                newsSnapshot.officialItems().getFirst().publishedAt()
        );
    }
}
