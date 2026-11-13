package org.predictiveedge.intelligence.connector;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("predictiveedge.coindesk")
public class CoinDeskProperties {
    private boolean enabled;
    private URI newsEndpoint = URI.create("https://data-api.coindesk.com/news/v1/search");
    private String apiKey = "";
    private Duration requestTimeout = Duration.ofSeconds(20);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public URI getNewsEndpoint() { return newsEndpoint; }
    public void setNewsEndpoint(URI newsEndpoint) { this.newsEndpoint = newsEndpoint; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }

    URI requiredEndpoint() {
        if (newsEndpoint == null || !newsEndpoint.isAbsolute() || !"https".equalsIgnoreCase(newsEndpoint.getScheme()))
            throw new IllegalStateException("predictiveedge.coindesk.news-endpoint must be an absolute HTTPS URL");
        return newsEndpoint;
    }

    String requiredApiKey() {
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalStateException("predictiveedge.coindesk.api-key is required when CoinDesk is enabled");
        return apiKey.trim();
    }

    Duration requiredTimeout() {
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative())
            throw new IllegalStateException("predictiveedge.coindesk.request-timeout must be positive");
        return requestTimeout;
    }
}
