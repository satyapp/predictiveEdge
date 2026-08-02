package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;

/** Current final-bar volume divided by the median of preceding completed bars. */
public final class RollingRelativeVolumeCalculator implements FeatureCalculator {
    private final FeatureDefinitionRef definitionRef;
    private final int baselineBars;

    public RollingRelativeVolumeCalculator(FeatureDefinitionRef definitionRef, int baselineBars) {
        this.definitionRef = Objects.requireNonNull(definitionRef, "Feature definition reference is required");
        if (baselineBars < 2) {
            throw new IllegalArgumentException("Relative-volume baseline must contain at least two bars");
        }
        this.baselineBars = baselineBars;
    }

    @Override
    public FeatureDefinitionRef definitionRef() {
        return definitionRef;
    }

    @Override
    public FeatureValue calculate(FeatureDefinition definition, FeatureInputManifest manifest, Instant computedAt) {
        FeatureCalculatorSupport.validate(definition, definitionRef, "baselineBars", baselineBars);
        var bars = manifest.bars();
        if (bars.size() < baselineBars + 1) {
            throw new IllegalStateException("Relative volume requires baseline bars plus a current bar");
        }
        var baseline = new ArrayList<Long>();
        bars.subList(bars.size() - baselineBars - 1, bars.size() - 1)
                .forEach(bar -> baseline.add(bar.values().volume()));
        baseline.sort(Long::compareTo);
        BigDecimal median;
        int middle = baseline.size() / 2;
        if (baseline.size() % 2 == 0) {
            median = BigDecimal.valueOf(baseline.get(middle - 1)).add(BigDecimal.valueOf(baseline.get(middle)))
                    .divide(BigDecimal.valueOf(2), FeatureCalculatorSupport.MATH_CONTEXT);
        } else {
            median = BigDecimal.valueOf(baseline.get(middle));
        }
        if (median.signum() == 0) {
            throw new IllegalStateException("Relative-volume baseline median is zero");
        }
        BigDecimal ratio = BigDecimal.valueOf(bars.getLast().values().volume())
                .divide(median, FeatureCalculatorSupport.MATH_CONTEXT);
        return FeatureValue.ready(definition, manifest, ratio, computedAt);
    }
}
