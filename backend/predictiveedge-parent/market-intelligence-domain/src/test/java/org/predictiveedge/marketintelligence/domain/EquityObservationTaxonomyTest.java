package org.predictiveedge.marketintelligence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

class EquityObservationTaxonomyTest {
    @Test
    void coversEveryGovernedCashEquityInformationFamily() {
        assertThat(EnumSet.allOf(ObservationKind.class)).contains(
                ObservationKind.TRADE,
                ObservationKind.L1_QUOTE,
                ObservationKind.ORDER_BOOK_SNAPSHOT,
                ObservationKind.ORDER_BOOK_DELTA,
                ObservationKind.BAR,
                ObservationKind.SERIES_VALUE,
                ObservationKind.MARKET_STATUS,
                ObservationKind.INSTRUMENT_STATUS,
                ObservationKind.UNIVERSE_MEMBERSHIP,
                ObservationKind.CORPORATE_ACTION,
                ObservationKind.CORPORATE_ANNOUNCEMENT,
                ObservationKind.FINANCIAL_STATEMENT,
                ObservationKind.EARNINGS_RELEASE,
                ObservationKind.OWNERSHIP_SNAPSHOT,
                ObservationKind.INSTITUTIONAL_FLOW,
                ObservationKind.BULK_DEAL,
                ObservationKind.BLOCK_DEAL,
                ObservationKind.DELIVERY_STATISTICS,
                ObservationKind.SHORT_SELLING_ACTIVITY,
                ObservationKind.SECURITIES_LENDING_ACTIVITY,
                ObservationKind.NEWS_EVENT,
                ObservationKind.MACRO_RELEASE);
    }

    @Test
    void keepsContextAggregationSeparateFromObservationSubjects() {
        assertThat(EnumSet.allOf(ContextScopeType.class))
                .containsExactlyInAnyOrder(
                        ContextScopeType.MARKET,
                        ContextScopeType.INDEX,
                        ContextScopeType.SECTOR,
                        ContextScopeType.INSTRUMENT);

        assertThat(EnumSet.allOf(ObservationSubjectType.class)).contains(
                ObservationSubjectType.VENUE,
                ObservationSubjectType.UNIVERSE,
                ObservationSubjectType.INDUSTRY,
                ObservationSubjectType.ISSUER,
                ObservationSubjectType.ECONOMY,
                ObservationSubjectType.MACRO_SERIES);
    }

    @Test
    void normalizesAndValidatesVersionedSchemaIdentity() {
        ObservationSchemaId schemaId = new ObservationSchemaId(" NSE.CASH-EQUITY.CORPORATE-ACTION.V2 ");

        assertThat(schemaId.value()).isEqualTo("nse.cash-equity.corporate-action.v2");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ObservationSchemaId("corporate-action"))
                .withMessageContaining("positive major version");
    }
}
