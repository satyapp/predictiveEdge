package org.predictiveedge.platform.eventing.application;

/**
 * Executes a handler and records its inbox outcome atomically.
 * Implementations return {@code DUPLICATE} without invoking the handler when the
 * consumer has already processed the event id.
 */
@FunctionalInterface
public interface InboxTransaction {
    ProcessingResult executeOnce(String consumerName, EventDelivery delivery, EventHandler handler);
}
