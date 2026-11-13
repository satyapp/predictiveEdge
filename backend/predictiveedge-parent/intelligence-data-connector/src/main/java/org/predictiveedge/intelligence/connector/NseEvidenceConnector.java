package org.predictiveedge.intelligence.connector;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

import org.predictiveedge.nse.mcp.NseMcpGateway;
import org.predictiveedge.nse.mcp.NseMcpServer;

public final class NseEvidenceConnector implements EvidenceSourceConnector {
    private static final ZoneId NSE_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Set<EvidenceDomain> SUPPORTED = Set.of(EvidenceDomain.MARKET_DATA,
            EvidenceDomain.CORPORATE_ACTIONS, EvidenceDomain.MACRO_AND_ECONOMIC,
            EvidenceDomain.INSTITUTIONAL_FLOWS);
    private final NseMcpGateway gateway;

    public NseEvidenceConnector(NseMcpGateway gateway) {
        this.gateway = java.util.Objects.requireNonNull(gateway, "NSE MCP gateway is required");
    }

    @Override public EvidenceSource source() { return EvidenceSource.NSE; }
    @Override public boolean supports(EvidenceDomain domain) { return SUPPORTED.contains(domain); }

    @Override
    public Object fetch(EvidenceDomain domain, EvidenceQuery query) {
        LocalDate cutoffDate = query.cutoff().atZone(NSE_ZONE).toLocalDate();
        return switch (domain) {
            case MARKET_DATA -> gateway.callTool(NseMcpServer.CM_MARKET, "cm_get_stock_quote",
                    Map.of("symbol", query.symbol()));
            case CORPORATE_ACTIONS -> gateway.callTool(NseMcpServer.BHAVCOPY, "get_corporate_actions", Map.of(
                    "symbol", query.symbol(),
                    "fromDate", query.attribute("corporateActionsFrom", cutoffDate.minusYears(5).toString()),
                    "toDate", cutoffDate.toString()));
            case MACRO_AND_ECONOMIC -> gateway.callTool(NseMcpServer.BHAVCOPY, "get_index_snapshot", Map.of(
                    "date", cutoffDate.toString(), "filter", query.attribute("indexFilter", "NIFTY")));
            case INSTITUTIONAL_FLOWS -> gateway.callTool(NseMcpServer.BHAVCOPY, "get_market_mood",
                    Map.of("date", cutoffDate.toString()));
            default -> throw new IllegalArgumentException("Unsupported NSE domain: " + domain);
        };
    }
}
