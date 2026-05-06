package com.redis.stockanalysisagent.semanticguardrail;

import com.redis.stockanalysisagent.semanticcache.SpringAiEmbeddingVectorizer;
import com.redis.vl.extensions.router.Route;
import com.redis.vl.extensions.router.RouteMatch;
import com.redis.vl.extensions.router.SemanticRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.UnifiedJedis;

import java.util.List;
import java.util.Optional;

@Service
public class SemanticGuardrailService {

    public static final String ALIEN_JOKES_ROUTE = "alien_jokes";
    public static final String CORPORATE_AGILE_ROUTE = "corporate_agile";

    private static final Logger log = LoggerFactory.getLogger(SemanticGuardrailService.class);

    private final boolean enabled;
    private final SemanticRouter router;

    public SemanticGuardrailService(
            SemanticGuardrailProperties properties,
            EmbeddingModel embeddingModel,
            @Value("${spring.data.redis.host:localhost}") String redisHost,
            @Value("${spring.data.redis.port:6378}") int redisPort
    ) {
        this.enabled = properties.isEnabled();

        if (!enabled) {
            this.router = null;
            return;
        }

        UnifiedJedis redisClient = new UnifiedJedis("redis://%s:%s".formatted(redisHost, redisPort));
        this.router = SemanticRouter.builder()
                .name(properties.getName())
                .jedis(redisClient)
                .vectorizer(new SpringAiEmbeddingVectorizer(
                        properties.getEmbeddingModelName(),
                        embeddingModel,
                        properties.getEmbeddingDimensions()
                ))
                .routes(List.of(
                        alienJokesRoute(properties),
                        corporateAgileRoute(properties)
                ))
                .overwrite(true)
                .build();
    }

    public Optional<GuardrailMatch> match(String userMessage) {
        if (!enabled || userMessage == null) {
            return Optional.empty();
        }

        String normalized = userMessage.trim();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        RouteMatch routeMatch = router.route(normalized);
        if (routeMatch == null || routeMatch.getName() == null) {
            return Optional.empty();
        }

        log.info("Semantic guardrail blocked route {} at distance {}", routeMatch.getName(), routeMatch.getDistance());
        return Optional.of(new GuardrailMatch(routeMatch.getName(), routeMatch.getDistance()));
    }

    private Route alienJokesRoute(SemanticGuardrailProperties properties) {
        return Route.builder()
                .name(ALIEN_JOKES_ROUTE)
                .references(List.of(
                        "tell me an alien joke",
                        "make a joke about aliens",
                        "funny alien joke",
                        "tell me a joke with an alien"
                ))
                .distanceThreshold(properties.getDistanceThreshold())
                .build();
    }

    private Route corporateAgileRoute(SemanticGuardrailProperties properties) {
        return Route.builder()
                .name(CORPORATE_AGILE_ROUTE)
                .references(List.of(
                        "corporate questions about agile",
                        "is scrum a good framework",
                        "should our team adopt scrum",
                        "kanban vs scrum for my organization",
                        "how should we run agile ceremonies"
                ))
                .distanceThreshold(properties.getDistanceThreshold())
                .build();
    }

    public record GuardrailMatch(
            String routeName,
            Double distance
    ) {
    }
}
