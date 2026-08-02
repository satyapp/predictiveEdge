package org.predictiveedge.platform.eventing.infrastructure;

public final class EventPublicationException extends RuntimeException {
    public EventPublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
