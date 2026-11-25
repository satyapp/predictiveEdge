package org.predictiveedge.intelligence.connector;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.predictiveedge.trendlyne.mcp.TrendlyneMcpGateway;

public final class TrendlyneEvidenceConnector implements EvidenceSourceConnector {
    private static final Set<EvidenceDomain> SUPPORTED = Set.of(
            EvidenceDomain.MARKET_DATA,
            EvidenceDomain.TECHNICAL_ANALYSIS,
            EvidenceDomain.FUNDAMENTAL_ANALYSIS,
            EvidenceDomain.NEWS_AND_SENTIMENT,
            EvidenceDomain.CORPORATE_ACTIONS,
            EvidenceDomain.INSTITUTIONAL_FLOWS,
            EvidenceDomain.HISTORICAL_AND_PREDICTIVE);

    private final TrendlyneMcpGateway gateway;

    public TrendlyneEvidenceConnector(TrendlyneMcpGateway gateway) {
        this.gateway = java.util.Objects.requireNonNull(gateway, "Trendlyne MCP gateway is required");
    }

    @Override
    public EvidenceSource source() {
        return EvidenceSource.TRENDLYNE;
    }

    @Override
    public boolean supports(EvidenceDomain domain) {
        return SUPPORTED.contains(domain);
    }

    /** Trendlyne reports plan and request errors as a successful call whose text says {@code "status": "error"}. */
    private static final Pattern ERROR_STATUS = Pattern.compile("\"status\"\\s*:\\s*\"error\"");

    @Override
    public Object fetch(EvidenceDomain domain, EvidenceQuery query) {
        return usable(switch (domain) {
            case MARKET_DATA, HISTORICAL_AND_PREDICTIVE -> gateway.callTool("get_stock_eod_ohlc",
                    Map.of("stock_code", query.symbol(), "period", query.attribute("trendlynePeriod", "monthly")));
            case TECHNICAL_ANALYSIS -> overview(query, "technical");
            case FUNDAMENTAL_ANALYSIS -> gateway.callTool("get_parameter_values_multi_stock",
                    Map.of("query", query.naturalLanguageQuery() + " for " + query.symbol(), "type", "stock"));
            case NEWS_AND_SENTIMENT -> overview(query, "news");
            case CORPORATE_ACTIONS -> overview(query, "events");
            case INSTITUTIONAL_FLOWS -> gateway.callTool("get_ownership_deals_insider_sast",
                    Map.of("stock_code", query.symbol(),
                            "type", query.attribute("trendlyneOwnershipType", "shareholding")));
            default -> throw new IllegalArgumentException("Unsupported Trendlyne domain: " + domain);
        });
    }

    private CallToolResult overview(EvidenceQuery query, String type) {
        return gateway.callTool("get_overview_news_corp_events",
                Map.of("stock_code", query.symbol(), "type", type));
    }

    /** Plan or request errors are unavailable, not evidence, so the source chain continues. */
    private static CallToolResult usable(CallToolResult result) {
        if (result == null) return null;
        if (Boolean.TRUE.equals(result.isError()) || hasErrorStatus(result)) {
            throw new EvidenceUnavailableException("Trendlyne did not return data for this request or plan");
        }
        return result;
    }

    private static boolean hasErrorStatus(CallToolResult result) {
        for (var content : result.content()) {
            if (content instanceof TextContent text && text.text() != null
                    && ERROR_STATUS.matcher(text.text()).find()) return true;
        }
        return result.structuredContent() != null && ERROR_STATUS.matcher(result.structuredContent().toString()).find();
    }
}
