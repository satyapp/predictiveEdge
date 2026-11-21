package org.predictiveedge.automate;

import java.net.URI;

/** Outbound port that POSTs a JSON payload to a FYERS Automate webhook. */
@FunctionalInterface
public interface AutomateWebhookSender {
    Result post(URI webhookUrl, String jsonPayload);

    record Result(Integer httpStatus, String error) {
        public boolean succeeded() { return error == null && httpStatus != null && httpStatus >= 200 && httpStatus < 300; }
    }
}
