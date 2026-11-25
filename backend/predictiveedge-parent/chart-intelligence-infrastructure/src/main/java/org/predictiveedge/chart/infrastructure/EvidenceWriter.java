package org.predictiveedge.chart.infrastructure;

import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.List;
import org.predictiveedge.intelligence.connector.EvidenceDomain;

/** Receives each fetched data type so it can be persisted before the stock is collated. */
interface EvidenceWriter {
    /** Writes nothing. Used when the evidence is only returned in memory. */
    EvidenceWriter DISCARD = new EvidenceWriter() {
        @Override
        public void write(String symbol, EvidenceDomain domain, ArrayNode evidence) {
            // Intentionally empty.
        }
    };

    /** Runs once, on the calling thread, before any worker starts. The symbols are distinct and normalized. */
    default void begin(List<String> symbols) {
        // Nothing to prepare by default.
    }

    /**
     * Runs on a fetch worker thread after one data type for one stock has been fetched. The array holds that
     * domain's provider evidence in the envelope's shape. Implementations must be thread-safe.
     */
    void write(String symbol, EvidenceDomain domain, ArrayNode evidence);
}
