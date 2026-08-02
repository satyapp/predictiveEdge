package org.predictiveedge.marketintelligence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class FeatureRegistryTest {

    @Test
    void resolvesOnlyTheExplicitRequestedVersion() {
        var versionOne = definition("1.0.0");
        var versionTwo = definition("2.0.0");
        var registry = new FeatureRegistry(List.of(versionOne, versionTwo));

        assertThat(registry.require(versionOne.ref())).isSameAs(versionOne);
        assertThat(registry.require(versionTwo.ref())).isSameAs(versionTwo);
        assertThatThrownBy(() -> registry.require(
                new FeatureDefinitionRef(new FeatureId("EMA_20"), "3.0.0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown feature definition");
    }

    @Test
    void rejectsDuplicateDefinitionIdentities() {
        var definition = definition("1.0.0");

        assertThatThrownBy(() -> new FeatureRegistry(List.of(definition, definition)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate feature definition");
    }

    @Test
    void canonicalizesFeatureIdsAndParameterOrder() {
        var definition = definition("1.0.0");

        assertThat(new FeatureId(" ema_20 ").value()).isEqualTo("EMA_20");
        assertThat(definition.parameters().keySet()).containsExactly("period", "price");
    }

    static FeatureDefinition definition(String version) {
        var parameters = new TreeMap<String, String>();
        parameters.put("price", "CLOSE");
        parameters.put("period", "20");
        return new FeatureDefinition(
                new FeatureDefinitionRef(new FeatureId("EMA_20"), version),
                FeatureFamily.DIRECTION,
                "EMA(close, 20)",
                FeatureUnit.PRICE,
                new BarInputRequirement(BarTimeframe.FIVE_MINUTES, 3, Duration.ofMinutes(30), false),
                parameters,
                new NumericPolicy(2, RoundingMode.HALF_UP, RoundingBoundary.FINAL_OUTPUT,
                        new BigDecimal("0.000001"), "decimal-v1"),
                "SMA seed over required bars",
                "Propagate unavailable",
                "Use split-adjusted price series",
                Duration.ofSeconds(2),
                "feature-kernel-1");
    }
}
