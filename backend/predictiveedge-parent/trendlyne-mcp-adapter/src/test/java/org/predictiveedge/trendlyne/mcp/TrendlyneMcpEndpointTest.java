package org.predictiveedge.trendlyne.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

class TrendlyneMcpEndpointTest {
    @Test
    void preservesSubscriptionPathAndQueryWithoutPuttingThemInTheBaseUrl() {
        var endpoint = TrendlyneMcpEndpoint.from(URI.create("https://mcp.example.test/customer/secret?plan=pro"));

        assertThat(endpoint.baseUrl()).isEqualTo("https://mcp.example.test");
        assertThat(endpoint.endpoint()).isEqualTo("/customer/secret?plan=pro");
    }

    @Test
    void supportsAnOriginOnlyUrl() {
        var endpoint = TrendlyneMcpEndpoint.from(URI.create("https://mcp.example.test"));

        assertThat(endpoint.endpoint()).isEqualTo("/");
    }
}
