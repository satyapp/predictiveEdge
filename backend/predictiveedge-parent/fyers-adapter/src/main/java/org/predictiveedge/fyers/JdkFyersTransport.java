package org.predictiveedge.fyers;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class JdkFyersTransport implements FyersTransport {
    private final HttpClient http;

    JdkFyersTransport(Duration timeout) {
        http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public Response send(HttpRequest request) throws IOException, InterruptedException {
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body());
    }
}
