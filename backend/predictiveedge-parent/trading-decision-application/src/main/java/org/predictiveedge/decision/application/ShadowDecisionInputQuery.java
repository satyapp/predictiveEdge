package org.predictiveedge.decision.application;

import java.time.Instant;
import org.predictiveedge.decision.domain.AITradingDecisionInputBundle;
import org.predictiveedge.decision.domain.ShadowScope;
import org.predictiveedge.decision.domain.TraderIntent;

/** Assembles the immutable point-in-time bundle from factual resource stores. */
@FunctionalInterface
public interface ShadowDecisionInputQuery {
    AITradingDecisionInputBundle assemble(ShadowScope scope, TraderIntent traderIntent, Instant cutoff);
}
