package com.redis.stockanalysisagent.news.sec;

import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;
import com.redis.stockanalysisagent.fundamentals.sec.SecProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SecNewsProviderTest {

    @Test
    void normalizesRecentSecFilingsIntoNewsSnapshot() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).ignoreExpectOrder(true).build();
        SecNewsProvider provider = new SecNewsProvider(restClientBuilder, properties());

        server.expect(requestTo("https://www.sec.gov/files/company_tickers.json"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "0": {
                            "cik_str": 320193,
                            "ticker": "AAPL",
                            "title": "Apple Inc."
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://data.sec.gov/submissions/CIK0000320193.json"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "filings": {
                            "recent": {
                              "accessionNumber": ["0000320193-26-000010", "0000320193-26-000009", "0000320193-26-000008"],
                              "filingDate": ["2026-03-15", "2026-02-01", "2026-01-15"],
                              "form": ["8-K", "10-Q", "4"],
                              "items": ["2.02,9.01", "", ""],
                              "primaryDocument": ["a8-k20260315.htm", "a10-q20260201.htm", "ownership.xml"],
                              "primaryDocDescription": ["Current Report", "Quarterly Report", "Statement of Changes in Beneficial Ownership"]
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        NewsSnapshot snapshot = provider.fetchSnapshot("AAPL");

        assertThat(snapshot.ticker()).isEqualTo("AAPL");
        assertThat(snapshot.companyName()).isEqualTo("Apple Inc.");
        assertThat(snapshot.source()).isEqualTo("sec");
        assertThat(snapshot.officialItems()).hasSize(2);
        assertThat(snapshot.webItems()).isEmpty();
        assertThat(snapshot.officialItems().get(0).publisher()).isEqualTo("SEC");
        assertThat(snapshot.officialItems().get(0).label()).isEqualTo("8-K");
        assertThat(snapshot.officialItems().get(0).publishedAt()).isEqualTo(LocalDate.parse("2026-03-15"));
        assertThat(snapshot.officialItems().get(0).title()).isEqualTo("Current Report");
        assertThat(snapshot.officialItems().get(0).summary()).contains("8-K items");
        assertThat(snapshot.officialItems().get(0).url())
                .isEqualTo("https://www.sec.gov/Archives/edgar/data/320193/000032019326000010/a8-k20260315.htm");
        assertThat(snapshot.officialItems().get(1).label()).isEqualTo("10-Q");

        server.verify();
    }

    private SecProperties properties() {
        SecProperties properties = new SecProperties();
        properties.setDataBaseUrl(URI.create("https://data.sec.gov"));
        properties.setTickerFileUrl(URI.create("https://www.sec.gov/files/company_tickers.json"));
        properties.setUserAgent("stock-analysis-agent-workshop workshop@example.com");
        return properties;
    }
}
