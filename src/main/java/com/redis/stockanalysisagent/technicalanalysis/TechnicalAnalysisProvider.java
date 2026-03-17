package com.redis.stockanalysisagent.technicalanalysis;

import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;

public interface TechnicalAnalysisProvider {

    TechnicalAnalysisSnapshot fetchSnapshot(String ticker);
}
