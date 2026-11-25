package org.predictiveedge.chart.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.predictiveedge.chart.application.SearchEvidenceCategory;
import org.predictiveedge.chart.application.StageOneActivityStore;
import org.predictiveedge.intelligence.connector.EvidenceDomain;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JPA implementation of {@link StageOneActivityStore}. Repositories come from Spring Data's factory so the
 * store can be built from the application's {@link EntityManagerFactory} without a component scan.
 */
public final class JpaStageOneActivityStore implements StageOneActivityStore {
    static final String STAGE_1 = "STAGE_1";

    private final TransactionTemplate transactions;
    private final ObjectMapper json;

    private final SearchResultRepository searchResults;
    private final SelectedStockRepository selectedStocks;
    private final SelectedStockAiAnalysisRepository selectedStockAiAnalyses;
    private final IntradayListRepository intradayLists;
    private final IntradayListItemRepository intradayListItems;

    private final MarketDataEvidenceRepository marketDataEvidence;
    private final TechnicalAnalysisEvidenceRepository technicalAnalysisEvidence;
    private final ChartPatternsEvidenceRepository chartPatternsEvidence;
    private final FundamentalAnalysisEvidenceRepository fundamentalAnalysisEvidence;
    private final NewsAndSentimentEvidenceRepository newsAndSentimentEvidence;
    private final CorporateActionsEvidenceRepository corporateActionsEvidence;
    private final MacroAndEconomicEvidenceRepository macroAndEconomicEvidence;
    private final InstitutionalFlowsEvidenceRepository institutionalFlowsEvidence;
    private final HistoricalAndPredictiveEvidenceRepository historicalAndPredictiveEvidence;
    private final MarketDepthEvidenceRepository marketDepthEvidence;
    private final PreOpenAnalysisEvidenceRepository preOpenAnalysisEvidence;

    private final SearchResultStockTopGainersRepository searchResultStockTopGainers;
    private final SearchResultStockTopLosersRepository searchResultStockTopLosers;
    private final SearchResultStockMostActiveByVolumeRepository searchResultStockMostActiveByVolume;
    private final SearchResultStockMostActiveByValueRepository searchResultStockMostActiveByValue;
    private final SearchResultStockWeek52HighRepository searchResultStockWeek52High;
    private final SearchResultStockWeek52LowRepository searchResultStockWeek52Low;
    private final SearchResultStockNearWeek52HighRepository searchResultStockNearWeek52High;
    private final SearchResultStockNearWeek52LowRepository searchResultStockNearWeek52Low;
    private final SearchResultStockIntradaySurgeRepository searchResultStockIntradaySurge;
    private final SearchResultStockIntradayFallRepository searchResultStockIntradayFall;
    private final SearchResultStockGapUpRepository searchResultStockGapUp;
    private final SearchResultStockGapDownRepository searchResultStockGapDown;
    private final SearchResultStockUpperCircuitRepository searchResultStockUpperCircuit;
    private final SearchResultStockLowerCircuitRepository searchResultStockLowerCircuit;
    private final SearchResultStockVolumeGainersRepository searchResultStockVolumeGainers;
    private final SearchResultStockFreshBreakoutsRepository searchResultStockFreshBreakouts;
    private final SearchResultStockNearBreakoutsRepository searchResultStockNearBreakouts;
    private final SearchResultStockTechnicalCrossoversRepository searchResultStockTechnicalCrossovers;
    private final SearchResultStockPreOpenRepository searchResultStockPreOpen;

    public JpaStageOneActivityStore(EntityManagerFactory entityManagers, PlatformTransactionManager transactionManager,
            ObjectMapper json) {
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "Transaction manager is required"));
        this.json = Objects.requireNonNull(json, "Object mapper is required");
        var repositories = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(
                Objects.requireNonNull(entityManagers, "Entity manager factory is required")));
        this.searchResults = repositories.getRepository(SearchResultRepository.class);
        this.selectedStocks = repositories.getRepository(SelectedStockRepository.class);
        this.selectedStockAiAnalyses = repositories.getRepository(SelectedStockAiAnalysisRepository.class);
        this.intradayLists = repositories.getRepository(IntradayListRepository.class);
        this.intradayListItems = repositories.getRepository(IntradayListItemRepository.class);
        this.marketDataEvidence = repositories.getRepository(MarketDataEvidenceRepository.class);
        this.technicalAnalysisEvidence = repositories.getRepository(TechnicalAnalysisEvidenceRepository.class);
        this.chartPatternsEvidence = repositories.getRepository(ChartPatternsEvidenceRepository.class);
        this.fundamentalAnalysisEvidence = repositories.getRepository(FundamentalAnalysisEvidenceRepository.class);
        this.newsAndSentimentEvidence = repositories.getRepository(NewsAndSentimentEvidenceRepository.class);
        this.corporateActionsEvidence = repositories.getRepository(CorporateActionsEvidenceRepository.class);
        this.macroAndEconomicEvidence = repositories.getRepository(MacroAndEconomicEvidenceRepository.class);
        this.institutionalFlowsEvidence = repositories.getRepository(InstitutionalFlowsEvidenceRepository.class);
        this.historicalAndPredictiveEvidence = repositories.getRepository(HistoricalAndPredictiveEvidenceRepository.class);
        this.marketDepthEvidence = repositories.getRepository(MarketDepthEvidenceRepository.class);
        this.preOpenAnalysisEvidence = repositories.getRepository(PreOpenAnalysisEvidenceRepository.class);
        this.searchResultStockTopGainers = repositories.getRepository(SearchResultStockTopGainersRepository.class);
        this.searchResultStockTopLosers = repositories.getRepository(SearchResultStockTopLosersRepository.class);
        this.searchResultStockMostActiveByVolume = repositories.getRepository(SearchResultStockMostActiveByVolumeRepository.class);
        this.searchResultStockMostActiveByValue = repositories.getRepository(SearchResultStockMostActiveByValueRepository.class);
        this.searchResultStockWeek52High = repositories.getRepository(SearchResultStockWeek52HighRepository.class);
        this.searchResultStockWeek52Low = repositories.getRepository(SearchResultStockWeek52LowRepository.class);
        this.searchResultStockNearWeek52High = repositories.getRepository(SearchResultStockNearWeek52HighRepository.class);
        this.searchResultStockNearWeek52Low = repositories.getRepository(SearchResultStockNearWeek52LowRepository.class);
        this.searchResultStockIntradaySurge = repositories.getRepository(SearchResultStockIntradaySurgeRepository.class);
        this.searchResultStockIntradayFall = repositories.getRepository(SearchResultStockIntradayFallRepository.class);
        this.searchResultStockGapUp = repositories.getRepository(SearchResultStockGapUpRepository.class);
        this.searchResultStockGapDown = repositories.getRepository(SearchResultStockGapDownRepository.class);
        this.searchResultStockUpperCircuit = repositories.getRepository(SearchResultStockUpperCircuitRepository.class);
        this.searchResultStockLowerCircuit = repositories.getRepository(SearchResultStockLowerCircuitRepository.class);
        this.searchResultStockVolumeGainers = repositories.getRepository(SearchResultStockVolumeGainersRepository.class);
        this.searchResultStockFreshBreakouts = repositories.getRepository(SearchResultStockFreshBreakoutsRepository.class);
        this.searchResultStockNearBreakouts = repositories.getRepository(SearchResultStockNearBreakoutsRepository.class);
        this.searchResultStockTechnicalCrossovers = repositories.getRepository(SearchResultStockTechnicalCrossoversRepository.class);
        this.searchResultStockPreOpen = repositories.getRepository(SearchResultStockPreOpenRepository.class);
    }

    @Override
    public void recordSearch(ScreenerSearch search) {
        readObject(search.criteriaJson(), "Criteria JSON");
        search.rows().forEach(row -> readObject(row.brokerRowJson(), "Broker row JSON"));
        transactions.executeWithoutResult(status -> {
            searchResults.saveAndFlush(new SearchResult(search.searchId(), search.userId(), search.category().name(),
                    search.screenType().name(), search.setup(), search.universe(), search.timeframe(),
                    search.tradingDate(), search.brokerAccountId(), search.criteriaJson(), search.rows().size(),
                    STAGE_1, search.searchedAt()));
            saveScreenerRows(search.category(), search.searchId(), search.searchedAt(), search.rows());
        });
    }

    @Override
    public void recordSelections(SelectionBatch batch) {
        for (SelectedStockInput stock : batch.stocks()) {
            readObject(stock.evidenceByDomainJson(), "Evidence JSON");
            readObject(stock.analysis().analysisJson(), "Analysis JSON");
        }
        transactions.executeWithoutResult(status -> {
            List<SelectedStock> selections = new ArrayList<>();
            for (SelectedStockInput stock : batch.stocks()) {
                if (selectedStocks.existsBySearchIdAndSymbol(batch.searchId(), stock.symbol()))
                    throw new IllegalStateException(stock.symbol() + " was already selected for this search");
                selections.add(new SelectedStock(stock.selectionId(), batch.searchId(), batch.category().name(),
                        batch.userId(), stock.symbol(), STAGE_1, batch.selectedAt()));
            }
            selectedStocks.saveAllAndFlush(selections);
            for (SelectedStockInput stock : batch.stocks()) {
                JsonNode evidenceByDomain = readObject(stock.evidenceByDomainJson(), "Evidence JSON");
                evidenceByDomain.fields().forEachRemaining(entry -> saveEvidence(
                        stock.selectionId(), domain(entry.getKey()), entry.getValue()));
                StageOneAnalysisInput analysis = stock.analysis();
                selectedStockAiAnalyses.save(new SelectedStockAiAnalysis(new AiAnalysisKey(stock.selectionId(), STAGE_1),
                        analysis.stance(), analysis.thesis(), analysis.confirmation(), analysis.risk(),
                        analysis.model(), analysis.analysisJson(), analysis.generatedAt()));
            }
        });
    }

    @Override
    public void recordIntradayList(IntradayListSave list) {
        transactions.executeWithoutResult(status -> {
            Map<UUID, SelectedStock> selections = new HashMap<>();
            selectedStocks.findAllById(list.selectionIds())
                    .forEach(selection -> selections.put(selection.getSelectionId(), selection));
            if (selections.size() != list.selectionIds().size())
                throw new IllegalArgumentException("Every selection must be saved before it joins an intraday list");
            List<IntradayListItem> items = new ArrayList<>();
            Set<String> symbols = new HashSet<>();
            for (UUID selectionId : list.selectionIds()) {
                SelectedStock selection = selections.get(selectionId);
                if (!selection.getUserId().equals(list.userId()))
                    throw new IllegalArgumentException("Selection belongs to another user");
                if (!selectedStockAiAnalyses.existsById(new AiAnalysisKey(selectionId, STAGE_1)))
                    throw new IllegalStateException(selection.getSymbol() + " has no Stage-1 analysis");
                if (!symbols.add(selection.getSymbol()))
                    throw new IllegalArgumentException(selection.getSymbol() + " is already on this intraday list");
                items.add(new IntradayListItem(new IntradayListItemKey(list.listId(), selectionId),
                        selection.getSymbol(), STAGE_1));
            }
            intradayLists.saveAndFlush(new IntradayList(list.listId(), list.userId(), STAGE_1, null, items.size(),
                    list.savedAt()));
            intradayListItems.saveAll(items);
        });
    }

    private void saveScreenerRows(SearchEvidenceCategory category, UUID searchId, Instant createdAt,
            List<ScreenerRow> rows) {
        switch (category) {
            case TOP_GAINERS -> searchResultStockTopGainers.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockTopGainers::new));
            case TOP_LOSERS -> searchResultStockTopLosers.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockTopLosers::new));
            case MOST_ACTIVE_BY_VOLUME -> searchResultStockMostActiveByVolume.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockMostActiveByVolume::new));
            case MOST_ACTIVE_BY_VALUE -> searchResultStockMostActiveByValue.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockMostActiveByValue::new));
            case WEEK_52_HIGH -> searchResultStockWeek52High.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockWeek52High::new));
            case WEEK_52_LOW -> searchResultStockWeek52Low.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockWeek52Low::new));
            case NEAR_WEEK_52_HIGH -> searchResultStockNearWeek52High.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockNearWeek52High::new));
            case NEAR_WEEK_52_LOW -> searchResultStockNearWeek52Low.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockNearWeek52Low::new));
            case INTRADAY_SURGE -> searchResultStockIntradaySurge.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockIntradaySurge::new));
            case INTRADAY_FALL -> searchResultStockIntradayFall.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockIntradayFall::new));
            case GAP_UP -> searchResultStockGapUp.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockGapUp::new));
            case GAP_DOWN -> searchResultStockGapDown.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockGapDown::new));
            case UPPER_CIRCUIT -> searchResultStockUpperCircuit.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockUpperCircuit::new));
            case LOWER_CIRCUIT -> searchResultStockLowerCircuit.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockLowerCircuit::new));
            case VOLUME_GAINERS -> searchResultStockVolumeGainers.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockVolumeGainers::new));
            case FRESH_BREAKOUTS -> searchResultStockFreshBreakouts.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockFreshBreakouts::new));
            case NEAR_BREAKOUTS -> searchResultStockNearBreakouts.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockNearBreakouts::new));
            case TECHNICAL_CROSSOVERS -> searchResultStockTechnicalCrossovers.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockTechnicalCrossovers::new));
            case PRE_OPEN -> searchResultStockPreOpen.saveAll(screenerRows(searchId, createdAt, rows, SearchResultStockPreOpen::new));
        }
    }

    private void saveEvidence(UUID selectionId, EvidenceDomain domain, JsonNode evidence) {
        switch (domain) {
            case MARKET_DATA -> marketDataEvidence.saveAll(evidenceRows(selectionId, domain, evidence, MarketDataEvidence::new));
            case TECHNICAL_ANALYSIS -> technicalAnalysisEvidence.saveAll(evidenceRows(selectionId, domain, evidence, TechnicalAnalysisEvidence::new));
            case CHART_PATTERNS -> chartPatternsEvidence.saveAll(evidenceRows(selectionId, domain, evidence, ChartPatternsEvidence::new));
            case FUNDAMENTAL_ANALYSIS -> fundamentalAnalysisEvidence.saveAll(evidenceRows(selectionId, domain, evidence, FundamentalAnalysisEvidence::new));
            case NEWS_AND_SENTIMENT -> newsAndSentimentEvidence.saveAll(evidenceRows(selectionId, domain, evidence, NewsAndSentimentEvidence::new));
            case CORPORATE_ACTIONS -> corporateActionsEvidence.saveAll(evidenceRows(selectionId, domain, evidence, CorporateActionsEvidence::new));
            case MACRO_AND_ECONOMIC -> macroAndEconomicEvidence.saveAll(evidenceRows(selectionId, domain, evidence, MacroAndEconomicEvidence::new));
            case INSTITUTIONAL_FLOWS -> institutionalFlowsEvidence.saveAll(evidenceRows(selectionId, domain, evidence, InstitutionalFlowsEvidence::new));
            case HISTORICAL_AND_PREDICTIVE -> historicalAndPredictiveEvidence.saveAll(evidenceRows(selectionId, domain, evidence, HistoricalAndPredictiveEvidence::new));
            case MARKET_DEPTH -> marketDepthEvidence.saveAll(evidenceRows(selectionId, domain, evidence, MarketDepthEvidence::new));
            case PRE_OPEN_ANALYSIS -> preOpenAnalysisEvidence.saveAll(evidenceRows(selectionId, domain, evidence, PreOpenAnalysisEvidence::new));
        }
    }

    private static <T> List<T> screenerRows(UUID searchId, Instant createdAt, List<ScreenerRow> rows,
            ScreenerRowFactory<T> factory) {
        return rows.stream().map(row -> factory.create(new SearchStockKey(searchId, row.symbol()),
                row.screenRank(), row.brokerRowJson(), createdAt)).toList();
    }

    private <T> List<T> evidenceRows(UUID selectionId, EvidenceDomain domain, JsonNode evidence,
            EvidenceRowFactory<T> factory) {
        if (!evidence.isArray()) throw new IllegalArgumentException(domain + " evidence must be a JSON array");
        Set<String> sources = new HashSet<>();
        List<T> rows = new ArrayList<>();
        for (JsonNode item : evidence) {
            if (!domain.name().equals(required(item, "domain")))
                throw new IllegalArgumentException("Evidence item does not belong to " + domain);
            String source = required(item, "source");
            if (!sources.add(source))
                throw new IllegalArgumentException(domain + " has more than one evidence row for " + source);
            JsonNode payload = item.get("payload");
            rows.add(factory.create(new EvidenceKey(selectionId, STAGE_1, source), required(item, "role"),
                    required(item, "status"), Instant.parse(required(item, "fetched_at")),
                    payload == null || payload.isNull() ? null : write(payload), optional(item, "detail")));
        }
        return rows;
    }

    private static EvidenceDomain domain(String value) {
        try {
            return EvidenceDomain.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Unknown evidence domain " + value, unknown);
        }
    }

    private JsonNode readObject(String value, String label) {
        try {
            JsonNode node = json.readTree(value);
            if (node == null || !node.isObject()) throw new IllegalArgumentException(label + " must be a JSON object");
            return node;
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException(label + " is not valid JSON", failure);
        }
    }

    private String write(JsonNode value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Evidence payload could not be serialized", failure);
        }
    }

    private static String required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank())
            throw new IllegalArgumentException("Evidence field " + field + " is required");
        return value.asText();
    }

    private static String optional(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    @FunctionalInterface
    private interface ScreenerRowFactory<T> {
        T create(SearchStockKey key, int screenRank, String brokerRow, Instant createdAt);
    }

    @FunctionalInterface
    private interface EvidenceRowFactory<T> {
        T create(EvidenceKey key, String role, String status, Instant fetchedAt, String payload, String detail);
    }
}
