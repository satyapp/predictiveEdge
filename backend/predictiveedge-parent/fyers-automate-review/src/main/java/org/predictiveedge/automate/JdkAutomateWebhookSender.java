package org.predictiveedge.automate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** POSTs JSON to FYERS Automate with the JDK HTTP client. Redirects are not followed. */
public final class JdkAutomateWebhookSender implements AutomateWebhookSender {
    private final HttpClient http;
    private final Duration timeout;

    public JdkAutomateWebhookSender(HttpClient http, Duration timeout) {
        this.http = Objects.requireNonNull(http);
        this.timeout = Objects.requireNonNull(timeout);
    }

    @Override
    public Result post(URI webhookUrl, String jsonPayload) {
        HttpRequest request = HttpRequest.newBuilder(webhookUrl).timeout(timeout)
                .header("Content-Type", "application/json")
                .header("User-Agent", "PredictiveEdge-Automate-Bridge/1")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload)).build();
        try {
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            return new Result(response.statusCode(), null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Result(null, "Delivery was interrupted");
        } catch (IOException exception) {
            return new Result(null, "FYERS Automate could not be reached: " + exception.getClass().getSimpleName());
        }
    }
}
