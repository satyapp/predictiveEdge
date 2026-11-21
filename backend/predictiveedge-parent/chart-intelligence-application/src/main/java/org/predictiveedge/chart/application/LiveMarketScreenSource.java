package org.predictiveedge.chart.application;

import java.util.List;
import java.util.UUID;
import org.predictiveedge.chart.domain.MarketScreenType;

/** Source port that converts live provider payloads into the canonical PredictiveEdge data copy. */
public interface LiveMarketScreenSource {
    List<Capability> capabilities();
    MarketScreenDataCopy fetch(MarketScreenType screenType, int maximumRows);

    default MarketScreenDataCopy fetch(FetchRequest request) {
        return fetch(request.screenType(), request.maximumRows());
    }

    record Capability(MarketScreenType screenType, boolean available, String sourceTool, String detail) { }

    record FetchRequest(UUID userId, MarketScreenType screenType, String setup, String universe,
            String timeframe, int maximumRows, String selectedSymbol) {
        public FetchRequest {
            if (screenType == null) throw new IllegalArgumentException("Screen type is required");
            if (maximumRows < 1 || maximumRows > 20)
                throw new IllegalArgumentException("Maximum rows must be between 1 and 20");
            setup = setup == null ? "" : setup.trim();
            universe = universe == null ? "" : universe.trim();
            timeframe = timeframe == null ? "" : timeframe.trim();
            selectedSymbol = selectedSymbol == null ? "" : selectedSymbol.trim();
        }
    }
}
