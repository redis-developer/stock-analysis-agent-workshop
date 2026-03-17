package com.redis.stockanalysisagent.chat.api;

import com.redis.stockanalysisagent.chat.StockAnalysisChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ChatControllerIntegrationTest.TestChatConfiguration.class)
class ChatControllerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private StockAnalysisChatService stockAnalysisChatService;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(stockAnalysisChatService);
    }

    @Test
    void proxiesMessagesThroughTheChatService() {
        when(stockAnalysisChatService.chat(eq("custom-user"), eq("session-123"), eq("What is the current price of Apple?")))
                .thenReturn(new StockAnalysisChatService.ChatTurn(
                        "custom-user:session-123",
                        "Apple is trading at $200.00.",
                        List.of("The user asked about Apple earlier.")
                ));

        ChatResponse response = client().post()
                .uri("/api/chat")
                .body(new ChatRequest("custom-user", "session-123", "What is the current price of Apple?"))
                .retrieve()
                .body(ChatResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo("custom-user");
        assertThat(response.sessionId()).isEqualTo("session-123");
        assertThat(response.conversationId()).isEqualTo("custom-user:session-123");
        assertThat(response.response()).isEqualTo("Apple is trading at $200.00.");
        assertThat(response.retrievedMemories()).containsExactly("The user asked about Apple earlier.");
    }

    @Test
    void exposesTheConfiguredUserIdForTheFrontend() {
        ChatContextResponse response = client().get()
                .uri("/api/chat/context")
                .retrieve()
                .body(ChatContextResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.defaultUserId()).isEqualTo("test-user");
    }

    @Test
    void generatesASessionIdWhenTheBrowserDoesNotSendOne() {
        when(stockAnalysisChatService.chat(eq("test-user"), anyString(), eq("hello")))
                .thenAnswer(invocation -> {
                    String generatedSessionId = invocation.getArgument(1, String.class);
                    return new StockAnalysisChatService.ChatTurn(
                            "test-user:" + generatedSessionId,
                            "hello back",
                            List.of()
                    );
                });

        ChatResponse response = client().post()
                .uri("/api/chat")
                .body(new ChatRequest(null, null, "hello"))
                .retrieve()
                .body(ChatResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo("test-user");
        assertThat(response.sessionId()).isNotBlank();
        assertThat(response.conversationId()).isEqualTo("test-user:" + response.sessionId());
        assertThat(response.response()).isEqualTo("hello back");
    }

    @Test
    void clearsTheBrowserSessionThroughTheChatService() {
        client().delete()
                .uri("/api/chat/session/session-123?userId=custom-user")
                .retrieve()
                .toBodilessEntity();

        verify(stockAnalysisChatService).clearSession("custom-user", "session-123");
    }

    @Test
    void rejectsBlankMessages() {
        assertThatThrownBy(() -> client().post()
                .uri("/api/chat")
                .body(new ChatRequest("custom-user", "session-123", "   "))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(RestClientResponseException.class)
                .extracting(ex -> ((RestClientResponseException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestChatConfiguration {

        @Bean
        @Primary
        StockAnalysisChatService stockAnalysisChatService() {
            return Mockito.mock(StockAnalysisChatService.class);
        }
    }
}
