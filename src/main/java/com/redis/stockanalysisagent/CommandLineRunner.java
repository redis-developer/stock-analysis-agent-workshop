package com.redis.stockanalysisagent;

import com.redis.stockanalysisagent.agent.CliOrchestrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.cli.enabled", havingValue = "true", matchIfMissing = false)
public class CommandLineRunner implements org.springframework.boot.CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandLineRunner.class);

    private final CliOrchestrationService cliOrchestrationService;
    private final ConfigurableApplicationContext applicationContext;

    public CommandLineRunner(
            CliOrchestrationService cliOrchestrationService,
            ConfigurableApplicationContext applicationContext
    ) {
        this.cliOrchestrationService = cliOrchestrationService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) {
        LOGGER.info("Running stock analysis CLI mode");
        cliOrchestrationService.processRequest();
        SpringApplication.exit(applicationContext, () -> 0);
    }
}
