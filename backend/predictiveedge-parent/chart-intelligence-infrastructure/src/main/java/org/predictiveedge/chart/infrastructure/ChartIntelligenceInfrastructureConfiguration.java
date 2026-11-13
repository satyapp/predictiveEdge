package org.predictiveedge.chart.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.predictiveedge.broker.spi.HistoricalMarketDataProvider;
import org.predictiveedge.broker.spi.LiveMarketDataInstrumentResolver;
import org.predictiveedge.chart.application.NsePreOpenCsvParser;
import org.predictiveedge.chart.application.NseTopMoversCsvParser;
import org.predictiveedge.chart.application.MarketScreenCsvAnalysisService;
import org.predictiveedge.chart.application.MarketScreenHistoricalAnalysisService;
import org.predictiveedge.chart.application.MarketScreenCsvParser;
import org.predictiveedge.chart.application.MarketScreenCsvParserRegistry;
import org.predictiveedge.chart.application.PreOpenCsvAnalysisService;
import org.predictiveedge.chart.application.PreOpenCandidateConfirmationService;
import org.predictiveedge.chart.application.PreOpenIntradayAnalysisService;
import org.predictiveedge.chart.domain.PreOpenCandidateRanker;
import org.predictiveedge.chart.domain.MarketScreenCandidateRanker;
import org.predictiveedge.chart.domain.MarketScreenTechnicalAnalyzer;
import org.predictiveedge.chart.domain.PreOpenIntradayAnalyzer;
import org.predictiveedge.marketintelligence.application.MarketBarQueryPort;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionQueryPort;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionPublicationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class ChartIntelligenceInfrastructureConfiguration {
    @Bean
    org.predictiveedge.chart.application.AnalysisRunHistoryStore analysisRunHistoryStore(JdbcTemplate jdbc) {
        return new JdbcAnalysisRunHistoryStore(jdbc);
    }

    @Bean
    JdbcChartSnapshotStore jdbcChartSnapshotStore(JdbcTemplate jdbc, ObjectMapper json) {
        return new JdbcChartSnapshotStore(jdbc, json);
    }

    @Bean
    PreOpenIntradayAnalyzer preOpenIntradayAnalyzer() {
        return new PreOpenIntradayAnalyzer(PreOpenIntradayAnalyzer.Policy.conservativeDefaults());
    }

    @Bean
    PreOpenCandidateRanker preOpenCandidateRanker() {
        return new PreOpenCandidateRanker();
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
    NseTopMoversCsvParser nseTopMoversCsvParser() {
        return new NseTopMoversCsvParser();
    }

    @Bean
    MarketScreenCandidateRanker marketScreenCandidateRanker() {
        return new MarketScreenCandidateRanker();
    }

    @Bean
    MarketScreenCsvParserRegistry marketScreenCsvParserRegistry(java.util.List<MarketScreenCsvParser> adapters) {
        return new MarketScreenCsvParserRegistry(adapters);
    }

    @Bean
    MarketScreenCsvAnalysisService marketScreenCsvAnalysisService(
            MarketScreenCsvParserRegistry parsers, MarketScreenCandidateRanker ranker) {
        return new MarketScreenCsvAnalysisService(parsers, ranker, Clock.systemUTC());
    }

    @Bean
    MarketScreenTechnicalAnalyzer marketScreenTechnicalAnalyzer() {
        return new MarketScreenTechnicalAnalyzer(MarketScreenTechnicalAnalyzer.Policy.conservativeDefaults());
    }

    @Bean
    MarketScreenHistoricalAnalysisService marketScreenHistoricalAnalysisService(
            MarketScreenCsvParserRegistry parsers, MarketScreenCandidateRanker ranker,
            LiveMarketDataInstrumentResolver instruments, HistoricalMarketDataProvider history,
            MarketScreenTechnicalAnalyzer analyzer) {
        return new MarketScreenHistoricalAnalysisService(parsers, ranker, instruments, history, analyzer,
                Clock.systemUTC());
    }

    @Bean
    PreOpenCsvAnalysisService preOpenCsvAnalysisService(
            NsePreOpenCsvParser parser,
            LiveMarketDataInstrumentResolver instruments,
            HistoricalMarketDataProvider history,
            PreOpenAuctionPublicationPort publications,
            PreOpenIntradayAnalyzer analyzer,
            PreOpenCandidateRanker ranker) {
        return new PreOpenCsvAnalysisService(parser, instruments, history, publications, analyzer, ranker,
                Clock.systemUTC());
    }

    @Bean
    PreOpenCandidateConfirmationService preOpenCandidateConfirmationService(
            PreOpenIntradayAnalysisService preOpen,
            MarketBarQueryPort bars,
            org.predictiveedge.chart.application.ChartSnapshotPublicationPort publications) {
        return new PreOpenCandidateConfirmationService(preOpen, bars, publications, Clock.systemUTC());
    }

    @Bean
    org.predictiveedge.chart.application.PreOpenOllamaAnalysisPort preOpenOllamaAnalysisPort(
            ObjectMapper json,
            @Value("${predictiveedge.ai.ollama.model:qwen3:8b}") String model,
            @Value("${predictiveedge.ai.ollama.endpoint:http://localhost:11434/api/chat}") URI endpoint,
            @Value("${predictiveedge.ai.ollama.timeout-seconds:360}") long timeoutSeconds,
            @Value("${predictiveedge.ai.ollama.context-window-tokens:8192}") int contextTokens,
            @Value("${predictiveedge.ai.ollama.maximum-output-tokens:1024}") int outputTokens) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        return new OllamaPreOpenAnalysisAdapter(json,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), endpoint, model,
                timeout, contextTokens, outputTokens, Clock.systemUTC());
    }

    @Bean
    org.predictiveedge.chart.application.MarketScreenOllamaAnalysisPort marketScreenOllamaAnalysisPort(
            ObjectMapper json,
            @Value("${predictiveedge.ai.ollama.model:qwen3:8b}") String model,
            @Value("${predictiveedge.ai.ollama.endpoint:http://localhost:11434/api/chat}") URI endpoint,
            @Value("${predictiveedge.ai.ollama.timeout-seconds:360}") long timeoutSeconds,
            @Value("${predictiveedge.ai.ollama.context-window-tokens:8192}") int contextTokens,
            @Value("${predictiveedge.ai.ollama.maximum-output-tokens:1024}") int outputTokens) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        return new OllamaMarketScreenAnalysisAdapter(json,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), endpoint, model,
                timeout, contextTokens, outputTokens, Clock.systemUTC());
    }
}
