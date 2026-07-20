package org.predictiveedge.broker.zerodha;

import java.net.URI;
import java.util.Map;

@FunctionalInterface
public interface ZerodhaSessionTerminationTransport {
    int delete(URI uri, Map<String, String> headers);
}
