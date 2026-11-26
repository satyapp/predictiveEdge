package org.predictiveedge.chart.infrastructure;

import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.Map;
import org.predictiveedge.intelligence.connector.EvidenceDomain;

/**
 * Where one run's evidence lives between fetching and collation. Fetch workers write each data type as they finish.
 * The collator reads a stock back only after every data type for that stock has been written.
 */
interface EvidenceStage extends EvidenceWriter {
    /**
     * Returns every data type stored for the stock, each as the JSON array of its provider evidence. Called from the
     * collator thread once all writes for {@code symbol} have returned.
     */
    Map<EvidenceDomain, ArrayNode> read(String symbol);
}
