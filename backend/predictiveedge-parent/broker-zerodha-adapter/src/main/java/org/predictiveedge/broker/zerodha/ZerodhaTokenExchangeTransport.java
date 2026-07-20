package org.predictiveedge.broker.zerodha;

import java.net.URI;
import java.util.Map;

@FunctionalInterface
public interface ZerodhaTokenExchangeTransport {
    String postForm(URI uri, Map<String, String> headers, Map<String, String> form);
}
