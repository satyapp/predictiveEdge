package org.predictiveedge.broker.zerodha;

import java.net.URI;
import java.util.Map;

@FunctionalInterface
public interface ZerodhaTransport {
    String get(URI uri, Map<String, String> headers);
}
