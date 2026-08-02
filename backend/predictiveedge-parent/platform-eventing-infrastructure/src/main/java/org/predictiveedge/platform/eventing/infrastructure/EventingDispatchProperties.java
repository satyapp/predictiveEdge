package org.predictiveedge.platform.eventing.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("predictiveedge.eventing.dispatch")
public class EventingDispatchProperties {
    private boolean enabled;
    private int batchSize = 100;
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration retryDelay = Duration.ofSeconds(10);
    private Duration publishTimeout = Duration.ofSeconds(10);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = positive(batchSize, "Batch size"); }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration value) { leaseDuration = positive(value, "Lease duration"); }
    public Duration getRetryDelay() { return retryDelay; }
    public void setRetryDelay(Duration value) { retryDelay = positive(value, "Retry delay"); }
    public Duration getPublishTimeout() { return publishTimeout; }
    public void setPublishTimeout(Duration value) { publishTimeout = positive(value, "Publish timeout"); }

    private static int positive(int value, String name) {
        if (value < 1) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
