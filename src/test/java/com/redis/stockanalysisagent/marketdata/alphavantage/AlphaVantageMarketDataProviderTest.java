package com.redis.stockanalysisagent.marketdata.alphavantage;

import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AlphaVantageMarketDataProviderTest {

    @Test
    void normalizesGlobalQuoteResponseIntoMarketSnapshot() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        AlphaVantageMarketDataProvider provider = new AlphaVantageMarketDataProvider(
                restClientBuilder,
                properties("demo")
        );

        server.expect(requestTo("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=AAPL&apikey=demo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "Global Quote": {
                            "01. symbol": "AAPL",
                            "05. price": "214.3200",
                            "07. latest trading day": "2026-03-14",
                            "08. previous close": "211.0100",
                            "09. change": "3.3100",
                            "10. change percent": "1.5686%"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        MarketSnapshot snapshot = provider.fetchSnapshot("AAPL");

        assertThat(snapshot.symbol()).isEqualTo("AAPL");
        assertThat(snapshot.currentPrice()).hasToString("214.32");
        assertThat(snapshot.previousClose()).hasToString("211.01");
        assertThat(snapshot.absoluteChange()).hasToString("3.31");
        assertThat(snapshot.percentChange()).hasToString("1.57");
        assertThat(snapshot.source()).isEqualTo("alpha-vantage");
        assertThat(snapshot.asOf()).isEqualTo(OffsetDateTime.parse("2026-03-14T00:00:00Z"));

        server.verify();
    }

    @Test
    void surfacesAlphaVantageNotesAsFailures() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        AlphaVantageMarketDataProvider provider = new AlphaVantageMarketDataProvider(
                restClientBuilder,
                properties("demo")
        );

        server.expect(requestTo("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=AAPL&apikey=demo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "Note": "Thank you for using Alpha Vantage! Our standard API rate limit is 25 requests per day."
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.fetchSnapshot("AAPL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Alpha Vantage note:");

        server.verify();
    }

    @Test
    void failsFastWhenAlphaVantageIsEnabledWithoutAnApiKey() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        AlphaVantageMarketDataProvider provider = new AlphaVantageMarketDataProvider(restClientBuilder, properties(""));

        assertThatThrownBy(() -> provider.fetchSnapshot("AAPL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Alpha Vantage market data is enabled");
    }

    private AlphaVantageProperties properties(String apiKey) {
        AlphaVantageProperties properties = new AlphaVantageProperties();
        properties.setBaseUrl(URI.create("https://www.alphavantage.co"));
        properties.setApiKey(apiKey);
        return properties;
    }
}
