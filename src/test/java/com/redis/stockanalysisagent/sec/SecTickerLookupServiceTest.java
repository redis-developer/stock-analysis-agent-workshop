package com.redis.stockanalysisagent.sec;

import com.redis.stockanalysisagent.cache.CacheNames;
import com.redis.stockanalysisagent.cache.ExternalDataCache;
import com.redis.stockanalysisagent.fundamentals.sec.SecProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecTickerLookupServiceTest {

    @Test
    void resolvesTickerFromCachedLinkedHashMapEntry() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        ExternalDataCache externalDataCache = new ExternalDataCache(cacheManager);
        SecProperties properties = new SecProperties();

        Map<String, Object> cachedReference = new LinkedHashMap<>();
        cachedReference.put("ticker", "AAPL");
        cachedReference.put("companyName", "Apple Inc.");
        cachedReference.put("cik", "0000320193");

        Map<String, Object> cachedIndex = new LinkedHashMap<>();
        cachedIndex.put("AAPL", cachedReference);
        cacheManager.getCache(CacheNames.SEC_TICKER_INDEX).put("all", cachedIndex);

        SecTickerLookupService lookupService = new SecTickerLookupService(
                RestClient.builder(),
                properties,
                externalDataCache
        );

        SecCompanyReference result = lookupService.resolve("AAPL");

        assertThat(result.ticker()).isEqualTo("AAPL");
        assertThat(result.companyName()).isEqualTo("Apple Inc.");
        assertThat(result.cik()).isEqualTo("0000320193");
    }
}
