package org.predictiveedge.guardian.domain;

/** Trade Guardian lifecycle; none of these states authorizes broker action. */
public enum MonitoringState {
    ACTIVE,
    SUSPENDED,
    COMPLETED
}
