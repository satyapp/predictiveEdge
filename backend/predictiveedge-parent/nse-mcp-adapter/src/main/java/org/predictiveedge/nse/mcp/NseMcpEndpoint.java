package org.predictiveedge.nse.mcp;

import java.net.URI;

record NseMcpEndpoint(String baseUrl, String endpoint) {
    static NseMcpEndpoint from(URI url) {
        String baseUrl = url.getScheme() + "://" + url.getRawAuthority();
        String path = url.getRawPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        String endpoint = url.getRawQuery() == null ? path : path + "?" + url.getRawQuery();
        return new NseMcpEndpoint(baseUrl, endpoint);
    }
}
