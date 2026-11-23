package org.predictiveedge.fyers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class FyersLiveMarketScreenSourceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void selectsTheScreenerEntryInsteadOfAParentConfigurationId() throws Exception {
        var config = json.readTree("""
                {
                  "s":"ok",
                  "data":{
                    "id":"configuration",
                    "screeners_standard":[
                      {
                        "id":"top_gainers",
                        "name":"Top Gainers",
                        "category":"Intraday Screeners",
                        "description":"Stocks with the biggest price increases",
                        "supported_params":["fields","universe","timeframe"],
                        "allowed_timeframes":["t0","c1_week"]
                      },
                      {"id":"top_losers","name":"Top Losers","category":"Intraday Screeners"}
                    ]
                  }
                }
                """);

        var selected = FyersLiveMarketScreenSource.findConfiguredEntry(
                config.path("data").path("screeners_standard"), List.of("top gainers", "gainers"));

        assertThat(selected.path("id").asText()).isEqualTo("top_gainers");
    }

    @Test
    void selectsTheRequestedUniverseFromTheUniverseArray() throws Exception {
        var config = json.readTree("""
                {"data":{"universes":[
                  {"id":"iw001","name":"Nifty 50"},
                  {"id":"iw010","name":"Nifty 500"},
                  {"id":"cs001","name":"All Stocks"}
                ]}}
                """);

        var selected = FyersLiveMarketScreenSource.findConfiguredEntry(
                config.path("data").path("universes"), List.of("nifty 500"));

        assertThat(selected.path("id").asText()).isEqualTo("iw010");
    }
}
