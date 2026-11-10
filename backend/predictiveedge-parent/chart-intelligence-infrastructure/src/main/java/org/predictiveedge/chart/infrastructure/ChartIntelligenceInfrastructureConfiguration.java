package org.predictiveedge.chart.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.predictiveedge.broker.spi.HistoricalMarketDataProvider;
import org.predictiveedge.broker.spi.LiveMarketDataInstrumentResolver;
import org.predictiveedge.chart.application.NsePreOpenCsvParser;
import org.predictiveedge.chart.application.PreOpenCsvAnalysisService;
import org.predictiveedge.chart.application.PreOpenIntradayAnalysisService;
import org.predictiveedge.chart.domain.PreOpenIntradayAnalyzer;
import org.predictiveedge.marketintelligence.application.MarketBarQueryPort;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionQueryPort;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionPublicationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class ChartIntelligenceInfrastructureConfiguration {
    @Bean
    JdbcChartSnapshotStore jdbcChartSnapshotStore(JdbcTemplate jdbc, ObjectMapper json) {
        return new JdbcChartSnapshotStore(jdbc, json);
    }

    @Bean
    PreOpenIntradayAnalyzer preOpenIntradayAnalyzer() {
        return new PreOpenIntradayAnalyzer(PreOpenIntradayAnalyzer.Policy.conservativeDefaults());
    }

    @Bean
    PreOpenIntradayAnalysisService preOpenIntradayAnalysisService(
            MarketBarQueryPort bars, PreOpenAuctionQueryPort preOpenSnapshots, PreOpenIntradayAnalyzer analyzer) {
        return new PreOpenIntradayAnalysisService(bars, preOpenSnapshots, analyzer);
    }

    @Bean
    NsePreOpenCsvParser nsePreOpenCsvParser() {
        return new NsePreOpenCsvParser();
    }

    @Bean
    PreOpenCsvAnalysisService preOpenCsvAnalysisService(
            NsePreOpenCsvParser parser,
            LiveMarketDataInstrumentResolver instruments,
            HistoricalMarketDataProvider history,
            PreOpenAuctionPublicationPort publications,
            PreOpenIntradayAnalyzer analyzer) {
        return new PreOpenCsvAnalysisService(parser, instruments, history, publications, analyzer, Clock.systemUTC());
    }
}
