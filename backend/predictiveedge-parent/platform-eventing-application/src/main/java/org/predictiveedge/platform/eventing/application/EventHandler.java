package org.predictiveedge.platform.eventing.application;

import org.predictiveedge.platform.eventing.contract.EventEnvelope;

/** Business handler invoked inside the inbox transaction. */
@FunctionalInterface
public interface EventHandler {
    void handle(EventEnvelope event);
}
