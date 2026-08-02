package org.predictiveedge.platform.eventing.infrastructure;

import org.predictiveedge.platform.eventing.application.DispatchSummary;
import org.predictiveedge.platform.eventing.application.OutboxDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs bounded dispatch cycles; overlapping execution is prevented by Spring's default scheduler. */
public final class EventOutboxScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(EventOutboxScheduler.class);
    private final OutboxDispatcher dispatcher;
    private final int batchSize;

    public EventOutboxScheduler(OutboxDispatcher dispatcher, int batchSize) {
        this.dispatcher = dispatcher;
        if (batchSize < 1) throw new IllegalArgumentException("Batch size must be positive");
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${predictiveedge.eventing.dispatch.fixed-delay:PT1S}")
    public void dispatch() {
        DispatchSummary summary = dispatcher.dispatch(batchSize);
        if (summary.claimed() > 0) {
            LOG.info("Event outbox dispatch claimed={}, published={}, failed={}",
                    summary.claimed(), summary.published(), summary.failed());
        }
    }
}
