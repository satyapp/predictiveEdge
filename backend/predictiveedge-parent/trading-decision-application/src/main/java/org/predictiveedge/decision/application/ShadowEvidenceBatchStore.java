package org.predictiveedge.decision.application;

import org.predictiveedge.decision.domain.ShadowEvidenceBatch;

/** Append-only ingestion boundary for one complete point-in-time evidence batch. */
@FunctionalInterface
public interface ShadowEvidenceBatchStore {
    boolean append(ShadowEvidenceBatch evidenceBatch);
}
