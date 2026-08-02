package org.predictiveedge.marketintelligence.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import org.junit.jupiter.api.Test;

class QualityPolicyTest {

    @Test
    void policyMustCoverEveryKnownIssueCode() {
        var incomplete = new EnumMap<QualityIssueCode, QualityRule>(QualityIssueCode.class);
        incomplete.put(QualityIssueCode.DUPLICATE_DELIVERY, allow());

        assertThatThrownBy(() -> new QualityPolicy("equity-quality-v1", incomplete, 9_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no rule");
    }

    @Test
    void blockingRuleMustForceConfidenceToZero() {
        assertThatThrownBy(() -> new QualityRule(
                QualitySeverity.CRITICAL, QualityAction.BLOCK, 100, 10, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must set confidence cap to zero");
    }

    static QualityPolicy policy() {
        var rules = new EnumMap<QualityIssueCode, QualityRule>(QualityIssueCode.class);
        for (QualityIssueCode code : QualityIssueCode.values()) {
            rules.put(code, allow());
        }
        rules.put(QualityIssueCode.INVALID_LINEAGE, block());
        rules.put(QualityIssueCode.INVALID_ENTITLEMENT, block());
        rules.put(QualityIssueCode.INVALID_SESSION, block());
        rules.put(QualityIssueCode.PROVISIONAL_INPUT, block());
        rules.put(QualityIssueCode.MANDATORY_SOURCE_MISSING, degrade(40, 60, true));
        rules.put(QualityIssueCode.SOURCE_CONFLICT, degrade(20, 70, false));
        rules.put(QualityIssueCode.COVERAGE_LOSS, degrade(30, 65, true));
        rules.put(QualityIssueCode.STALE_FALLBACK, degrade(50, 40, false));
        rules.put(QualityIssueCode.FEATURE_UNAVAILABLE, degrade(50, 50, true));
        rules.put(QualityIssueCode.FEATURE_WARMING_UP, degrade(30, 70, true));
        rules.put(QualityIssueCode.FEATURE_STALE, degrade(50, 40, true));
        rules.put(QualityIssueCode.FEATURE_INVALID, block());
        return new QualityPolicy("equity-quality-v1", rules, 9_000);
    }

    private static QualityRule allow() {
        return new QualityRule(QualitySeverity.INFO, QualityAction.ALLOW, 0, 100, false);
    }

    private static QualityRule block() {
        return new QualityRule(QualitySeverity.CRITICAL, QualityAction.BLOCK, 100, 0, false);
    }

    private static QualityRule degrade(int penalty, int cap, boolean unknown) {
        return new QualityRule(QualitySeverity.ERROR, QualityAction.DEGRADE, penalty, cap, unknown);
    }
}
