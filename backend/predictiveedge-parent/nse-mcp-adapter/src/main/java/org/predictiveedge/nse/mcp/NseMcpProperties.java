package org.predictiveedge.nse.mcp;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("predictiveedge.nse.mcp")
public class NseMcpProperties {
    static final URI DEFAULT_BHAVCOPY_URL = URI.create("https://mcp.nseindia.in/bhavcopy/cm/mcp");
    static final URI DEFAULT_CM_MARKET_URL = URI.create("https://mcp.nseindia.in/cmmkt/mcp");

    private boolean enabled;
    private URI bhavcopyUrl = DEFAULT_BHAVCOPY_URL;
    private URI cmMarketUrl = DEFAULT_CM_MARKET_URL;
    private Duration requestTimeout = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public URI getBhavcopyUrl() {
        return bhavcopyUrl;
    }

    public void setBhavcopyUrl(URI bhavcopyUrl) {
        this.bhavcopyUrl = bhavcopyUrl;
    }

    public URI getCmMarketUrl() {
        return cmMarketUrl;
    }

    public void setCmMarketUrl(URI cmMarketUrl) {
        this.cmMarketUrl = cmMarketUrl;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    URI requiredUrl(NseMcpServer server) {
        URI url = switch (server) {
            case BHAVCOPY -> bhavcopyUrl;
            case CM_MARKET -> cmMarketUrl;
        };
        if (url == null || !url.isAbsolute()) {
            throw new IllegalStateException(propertyName(server) + " must be an absolute URL");
        }
        if (!"https".equalsIgnoreCase(url.getScheme()) && !"http".equalsIgnoreCase(url.getScheme())) {
            throw new IllegalStateException(propertyName(server) + " must use http or https");
        }
        if (url.getFragment() != null || url.getUserInfo() != null) {
            throw new IllegalStateException(propertyName(server) + " must not contain user-info or a fragment");
        }
        return url;
    }

    Duration requiredRequestTimeout() {
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalStateException("predictiveedge.nse.mcp.request-timeout must be positive");
        }
        return requestTimeout;
    }

    private static String propertyName(NseMcpServer server) {
        return switch (server) {
            case BHAVCOPY -> "predictiveedge.nse.mcp.bhavcopy-url";
            case CM_MARKET -> "predictiveedge.nse.mcp.cm-market-url";
        };
    }
}
