package org.predictiveedge.chart.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class OllamaMarketScreenAnalysisAdapterTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void retainsDeterministicDirectionsAndNormalizesFyersSymbols() throws Exception {
        var content = json.readTree("""
                {"candidates":[
                  {"symbol":"NSE:ITI-EQ","stance":"SHORT_WATCH","thesis":"Model view",
                   "confirmation":"Confirm","risk":"Risk"}
                ]}
                """);
        var expected = new LinkedHashMap<String, String>();
        expected.put("ITI", "LONG_WATCH");
        var cautions = new ArrayList<String>();

        var result = OllamaMarketScreenAnalysisAdapter.reconcileCandidates(content, expected, cautions);

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.symbol()).isEqualTo("ITI");
            assertThat(candidate.stance()).isEqualTo("LONG_WATCH");
            assertThat(candidate.thesis()).isEqualTo("Model view");
        });
        assertThat(cautions).anyMatch(value -> value.contains("deterministic stance LONG_WATCH was retained"));
    }

    @Test
    void keepsEveryExpectedStockWhenOllamaOmitsOrAddsSymbols() throws Exception {
        var content = json.readTree("""
                {"candidates":[
                  {"symbol":"UNKNOWN","stance":"WAIT","thesis":"Unknown",
                   "confirmation":"None","risk":"Unknown"}
                ]}
                """);
        var expected = new LinkedHashMap<String, String>();
        expected.put("ITI", "LONG_WATCH");
        expected.put("ECLERX", "WAIT");
        var cautions = new ArrayList<String>();

        var result = OllamaMarketScreenAnalysisAdapter.reconcileCandidates(content, expected, cautions);

        assertThat(result).extracting(candidate -> candidate.symbol())
                .containsExactly("ITI", "ECLERX");
        assertThat(result).extracting(candidate -> candidate.stance())
                .containsExactly("LONG_WATCH", "WAIT");
        assertThat(cautions).anyMatch(value -> value.contains("unknown symbol UNKNOWN"));
        assertThat(cautions).anyMatch(value -> value.contains("omitted ITI"));
    }
}
