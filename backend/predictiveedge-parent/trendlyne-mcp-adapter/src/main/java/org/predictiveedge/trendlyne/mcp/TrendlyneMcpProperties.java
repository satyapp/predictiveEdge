package org.predictiveedge.trendlyne.mcp;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("predictiveedge.trendlyne.mcp")
public class TrendlyneMcpProperties {
    private boolean enabled;
    private URI url;
    private Duration requestTimeout = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public URI getUrl() {
        return url;
    }

    public void setUrl(URI url) {
        this.url = url;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    URI requiredUrl() {
        if (url == null || !url.isAbsolute()) {
            throw new IllegalStateException("predictiveedge.trendlyne.mcp.url must be an absolute URL when Trendlyne MCP is enabled");
        }
        if (!"https".equalsIgnoreCase(url.getScheme()) && !"http".equalsIgnoreCase(url.getScheme())) {
            throw new IllegalStateException("predictiveedge.trendlyne.mcp.url must use http or https");
        }
        if (url.getFragment() != null || url.getUserInfo() != null) {
            throw new IllegalStateException("predictiveedge.trendlyne.mcp.url must not contain user-info or a fragment");
        }
        return url;
    }

    Duration requiredRequestTimeout() {
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalStateException("predictiveedge.trendlyne.mcp.request-timeout must be positive");
        }
        return requestTimeout;
    }
}
