package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Exact initial production definitions and calculator bindings for liquid NSE cash equities. */
public final class CoreEquityFeatureProfile {
    public static final String VERSION = "nse-cash-intraday-v1";
    public static final FeatureDefinitionRef EMA_20 = ref("EMA_20");
    public static final FeatureDefinitionRef EMA_50 = ref("EMA_50");
    public static final FeatureDefinitionRef PLUS_DI_14 = ref("PLUS_DI_14");
    public static final FeatureDefinitionRef MINUS_DI_14 = ref("MINUS_DI_14");
    public static final FeatureDefinitionRef ADX_14 = ref("ADX_14");
    public static final FeatureDefinitionRef BOLLINGER_WIDTH_20 = ref("BOLLINGER_WIDTH_20");
    public static final FeatureDefinitionRef SESSION_VWAP_APPROX = ref("SESSION_VWAP_APPROX");
    public static final FeatureDefinitionRef DONCHIAN_UPPER_20 = ref("DONCHIAN_UPPER_20");
    public static final FeatureDefinitionRef DONCHIAN_LOWER_20 = ref("DONCHIAN_LOWER_20");
    public static final FeatureDefinitionRef RSI_14 = ref("RSI_14");
    public static final FeatureDefinitionRef ATR_14 = ref("ATR_14");
    public static final FeatureDefinitionRef ROLLING_RVOL_20 = ref("ROLLING_RVOL_20");
    public static final FeatureDefinitionRef TIME_ADJUSTED_RVOL = ref("TIME_ADJUSTED_RVOL");
    public static final FeatureDefinitionRef RELATIVE_LEADERSHIP_20 = ref("RELATIVE_LEADERSHIP_20");

    private CoreEquityFeatureProfile() { }

    public static FeatureRegistry definitions() { return new FeatureRegistry(definitionList()); }

    public static FeatureCalculationEngine calculators() {
        return new FeatureCalculationEngine(List.of(
                new ExponentialMovingAverageCalculator(EMA_20, 20),
                new ExponentialMovingAverageCalculator(EMA_50, 50),
                new DirectionalMovementCalculator(PLUS_DI_14, 14, DirectionalMovementComponent.PLUS_DI),
                new DirectionalMovementCalculator(MINUS_DI_14, 14, DirectionalMovementComponent.MINUS_DI),
                new DirectionalMovementCalculator(ADX_14, 14, DirectionalMovementComponent.ADX),
                new BollingerBandWidthCalculator(BOLLINGER_WIDTH_20, 20, new BigDecimal("2")),
                new SessionVwapCalculator(SESSION_VWAP_APPROX),
                new PriorBarDonchianCalculator(DONCHIAN_UPPER_20, 20, DonchianComponent.UPPER),
                new PriorBarDonchianCalculator(DONCHIAN_LOWER_20, 20, DonchianComponent.LOWER),
                new RelativeStrengthIndexCalculator(RSI_14, 14),
                new AverageTrueRangeCalculator(ATR_14, 14),
                new RollingRelativeVolumeCalculator(ROLLING_RVOL_20, 20)));
    }

    private static List<FeatureDefinition> definitionList() {
        var values = new ArrayList<FeatureDefinition>();
        values.add(definition(EMA_20, FeatureFamily.DIRECTION, FeatureUnit.PRICE, BarTimeframe.FIFTEEN_MINUTES,
                20, false, params("period", "20"), "EMA(close,20), SMA seed"));
        values.add(definition(EMA_50, FeatureFamily.DIRECTION, FeatureUnit.PRICE, BarTimeframe.FIFTEEN_MINUTES,
                50, false, params("period", "50"), "EMA(close,50), SMA seed"));
        values.add(definition(PLUS_DI_14, FeatureFamily.TREND_QUALITY, FeatureUnit.PERCENT,
                BarTimeframe.FIFTEEN_MINUTES, 28, false, params("period", "14"), "Wilder +DI(14)"));
        values.add(definition(MINUS_DI_14, FeatureFamily.TREND_QUALITY, FeatureUnit.PERCENT,
                BarTimeframe.FIFTEEN_MINUTES, 28, false, params("period", "14"), "Wilder -DI(14)"));
        values.add(definition(ADX_14, FeatureFamily.TREND_QUALITY, FeatureUnit.PERCENT,
                BarTimeframe.FIFTEEN_MINUTES, 28, false, params("period", "14"), "Wilder ADX(14)"));
        var bollinger = params("period", "20"); bollinger.put("standardDeviations", "2");
        values.add(definition(BOLLINGER_WIDTH_20, FeatureFamily.REGIME, FeatureUnit.PERCENT,
                BarTimeframe.FIFTEEN_MINUTES, 20, false, bollinger, "Bollinger width(20,2), population SD"));
        values.add(definition(SESSION_VWAP_APPROX, FeatureFamily.INTRADAY_LOCATION, FeatureUnit.PRICE,
                BarTimeframe.FIVE_MINUTES, 1, true, new TreeMap<>(), "Session typical-price x volume approximation"));
        values.add(definition(DONCHIAN_UPPER_20, FeatureFamily.TRIGGER, FeatureUnit.PRICE,
                BarTimeframe.FIVE_MINUTES, 21, false, params("period", "20"), "Highest prior 20 highs; signal bar excluded"));
        values.add(definition(DONCHIAN_LOWER_20, FeatureFamily.TRIGGER, FeatureUnit.PRICE,
                BarTimeframe.FIVE_MINUTES, 21, false, params("period", "20"), "Lowest prior 20 lows; signal bar excluded"));
        values.add(definition(RSI_14, FeatureFamily.MOMENTUM, FeatureUnit.PERCENT,
                BarTimeframe.FIVE_MINUTES, 15, false, params("period", "14"), "Wilder RSI(14)"));
        values.add(definition(ATR_14, FeatureFamily.RISK_DISTANCE, FeatureUnit.PRICE,
                BarTimeframe.FIVE_MINUTES, 15, false, params("period", "14"), "Wilder ATR(14)"));
        values.add(definition(ROLLING_RVOL_20, FeatureFamily.PARTICIPATION, FeatureUnit.RATIO,
                BarTimeframe.FIVE_MINUTES, 21, false, params("baselineBars", "20"),
                "Current volume / median prior 20 completed bars; interim baseline"));
        values.add(definition(TIME_ADJUSTED_RVOL, FeatureFamily.PARTICIPATION, FeatureUnit.RATIO,
                BarTimeframe.FIVE_MINUTES, 1, true, params("baseline", "HISTORICAL_SAME_SESSION_SLOT_MEDIAN"),
                "Current final volume / historical same-session-slot median"));
        values.add(definition(RELATIVE_LEADERSHIP_20, FeatureFamily.RELATIVE_LEADERSHIP, FeatureUnit.PERCENT,
                BarTimeframe.FIFTEEN_MINUTES, 20, false, params("period", "20"),
                "Percent change in synchronized instrument/benchmark close ratio over 20 final bars"));
        return List.copyOf(values);
    }

    private static FeatureDefinition definition(FeatureDefinitionRef ref, FeatureFamily family, FeatureUnit unit,
            BarTimeframe timeframe, int bars, boolean reset, TreeMap<String, String> parameters, String formula) {
        return new FeatureDefinition(ref, family, formula, unit,
                new BarInputRequirement(timeframe, bars, timeframe == BarTimeframe.FIFTEEN_MINUTES
                        ? Duration.ofMinutes(45) : Duration.ofMinutes(15), reset), parameters,
                new NumericPolicy(6, RoundingMode.HALF_UP, RoundingBoundary.FINAL_OUTPUT,
                        new BigDecimal("0.000001"), "decimal-v1"), "Definition-specific Wilder/SMA seed",
                "Unavailable remains unknown", "Split-adjusted price series", Duration.ZERO, "feature-kernel-v1");
    }

    private static TreeMap<String, String> params(String key, String value) {
        var parameters = new TreeMap<String, String>(); parameters.put(key, value); return parameters;
    }

    private static FeatureDefinitionRef ref(String id) {
        return new FeatureDefinitionRef(new FeatureId(id), VERSION);
    }
}
