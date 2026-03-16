package com.redis.stockanalysisagent.fundamentals.sec;

import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SecFundamentalsProviderTest {

    @Test
    void normalizesCompanyFactsIntoFundamentalsSnapshot() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        SecFundamentalsProvider provider = new SecFundamentalsProvider(restClientBuilder, properties());

        server.expect(requestTo("https://www.sec.gov/files/company_tickers.json"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("User-Agent", "stock-analysis-agent-workshop workshop@example.com"))
                .andRespond(withSuccess("""
                        {
                          "0": {
                            "cik_str": 320193,
                            "ticker": "AAPL",
                            "title": "Apple Inc."
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://data.sec.gov/api/xbrl/companyfacts/CIK0000320193.json"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("User-Agent", "stock-analysis-agent-workshop workshop@example.com"))
                .andRespond(withSuccess("""
                        {
                          "facts": {
                            "us-gaap": {
                              "RevenueFromContractWithCustomerExcludingAssessedTax": {
                                "units": {
                                  "USD": [
                                    {
                                      "start": "2023-09-30",
                                      "end": "2024-09-28",
                                      "val": 400000000000,
                                      "fy": 2024,
                                      "fp": "FY",
                                      "form": "10-K",
                                      "filed": "2024-11-01"
                                    },
                                    {
                                      "start": "2022-10-01",
                                      "end": "2023-09-30",
                                      "val": 380000000000,
                                      "fy": 2023,
                                      "fp": "FY",
                                      "form": "10-K",
                                      "filed": "2023-11-03"
                                    }
                                  ]
                                }
                              },
                              "NetIncomeLoss": {
                                "units": {
                                  "USD": [
                                    {
                                      "start": "2023-09-30",
                                      "end": "2024-09-28",
                                      "val": 100000000000,
                                      "fy": 2024,
                                      "fp": "FY",
                                      "form": "10-K",
                                      "filed": "2024-11-01"
                                    }
                                  ]
                                }
                              },
                              "OperatingIncomeLoss": {
                                "units": {
                                  "USD": [
                                    {
                                      "start": "2023-09-30",
                                      "end": "2024-09-28",
                                      "val": 120000000000,
                                      "fy": 2024,
                                      "fp": "FY",
                                      "form": "10-K",
                                      "filed": "2024-11-01"
                                    }
                                  ]
                                }
                              },
                              "CashAndCashEquivalentsAtCarryingValue": {
                                "units": {
                                  "USD": [
                                    {
                                      "end": "2024-09-28",
                                      "val": 30000000000,
                                      "fy": 2024,
                                      "fp": "FY",
                                      "form": "10-K",
                                      "filed": "2024-11-01"
                                    }
                                  ]
                                }
                              },
                              "LongTermDebtNoncurrent": {
                                "units": {
                                  "USD": [
                                    {
                                      "end": "2024-09-28",
                                      "val": 90000000000,
                                      "fy": 2024,
                                      "fp": "FY",
                                      "form": "10-K",
                                      "filed": "2024-11-01"
                                    }
                                  ]
                                }
                              },
                              "EarningsPerShareDiluted": {
                                "units": {
                                  "USD/shares": [
                                    {
                                      "start": "2023-09-30",
                                      "end": "2024-09-28",
                                      "val": 6.5,
                                      "fy": 2024,
                                      "fp": "FY",
                                      "form": "10-K",
                                      "filed": "2024-11-01"
                                    }
                                  ]
                                }
                              }
                            },
                            "dei": {
                              "EntityCommonStockSharesOutstanding": {
                                "units": {
                                  "shares": [
                                    {
                                      "end": "2024-09-28",
                                      "val": 15000000000,
                                      "fy": 2024,
                                      "fp": "FY",
                                      "form": "10-K",
                                      "filed": "2024-11-01"
                                    }
                                  ]
                                }
                              }
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        MarketSnapshot marketSnapshot = new MarketSnapshot(
                "AAPL",
                new BigDecimal("200.00"),
                new BigDecimal("198.00"),
                new BigDecimal("2.00"),
                new BigDecimal("1.01"),
                OffsetDateTime.parse("2026-03-16T00:00:00Z"),
                "alpha-vantage"
        );

        FundamentalsSnapshot snapshot = provider.fetchSnapshot("AAPL", Optional.of(marketSnapshot));

        assertThat(snapshot.ticker()).isEqualTo("AAPL");
        assertThat(snapshot.companyName()).isEqualTo("Apple Inc.");
        assertThat(snapshot.cik()).isEqualTo("0000320193");
        assertThat(snapshot.revenue()).hasToString("400000000000.00");
        assertThat(snapshot.previousRevenue()).hasToString("380000000000.00");
        assertThat(snapshot.revenueGrowthPercent()).hasToString("5.26");
        assertThat(snapshot.netIncome()).hasToString("100000000000.00");
        assertThat(snapshot.operatingIncome()).hasToString("120000000000.00");
        assertThat(snapshot.operatingMarginPercent()).hasToString("30.00");
        assertThat(snapshot.netMarginPercent()).hasToString("25.00");
        assertThat(snapshot.cashAndCashEquivalents()).hasToString("30000000000.00");
        assertThat(snapshot.longTermDebt()).hasToString("90000000000.00");
        assertThat(snapshot.sharesOutstanding()).hasToString("15000000000.00");
        assertThat(snapshot.currentPrice()).hasToString("200.00");
        assertThat(snapshot.marketCap()).hasToString("3000000000000.00");
        assertThat(snapshot.priceToSales()).hasToString("7.50");
        assertThat(snapshot.earningsPerShare()).hasToString("6.50");
        assertThat(snapshot.priceToEarnings()).hasToString("30.77");
        assertThat(snapshot.fiscalYearEnd()).hasToString("2024-09-28");
        assertThat(snapshot.filedAt()).hasToString("2024-11-01");
        assertThat(snapshot.source()).isEqualTo("sec");

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
