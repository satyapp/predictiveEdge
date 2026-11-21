package org.predictiveedge.fyers;

import java.io.IOException;
import java.net.http.HttpRequest;

interface FyersTransport {
    Response send(HttpRequest request) throws IOException, InterruptedException;

    record Response(int statusCode, String body) { }
}
