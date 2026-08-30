package org.predictiveedge.decision.application;

import java.time.Instant;
import java.util.List;
import org.predictiveedge.decision.domain.IntelligenceFeedback;
import org.predictiveedge.decision.domain.TraderIntent;

/** Read port that obtains point-in-time feedback without coupling intelligence modules to each other. */
public interface IntelligenceFeedbackQuery {
    List<IntelligenceFeedback> latestFor(TraderIntent intent, Instant knowledgeCutoff);
}
