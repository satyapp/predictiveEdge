package org.predictiveedge.trendlyne.mcp;

import java.net.URI;

record TrendlyneMcpEndpoint(String baseUrl, String endpoint) {
    static TrendlyneMcpEndpoint from(URI url) {
        String baseUrl = url.getScheme() + "://" + url.getRawAuthority();
        String path = url.getRawPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        String endpoint = url.getRawQuery() == null ? path : path + "?" + url.getRawQuery();
        return new TrendlyneMcpEndpoint(baseUrl, endpoint);
    }
}
