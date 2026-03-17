package com.redis.stockanalysisagent.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatFrontendIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void servesChatInterfaceAtRoot() {
        String page = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build()
                .get()
                .uri("/")
                .retrieve()
                .body(String.class);

        assertThat(page).contains("<title>Stock Analysis Chat</title>");
        assertThat(page).contains("Ask the stock agent anything");
        assertThat(page).contains("chat.css");
        assertThat(page).contains("chat.js");
        assertThat(page).contains("same memory-backed chat flow as the CLI");
        assertThat(page).doesNotContain("placeholder=\"AAPL\"");
        assertThat(page).contains("id=\"user-id-input\"");
        assertThat(page).contains("id=\"session-id-value\"");
    }
}
