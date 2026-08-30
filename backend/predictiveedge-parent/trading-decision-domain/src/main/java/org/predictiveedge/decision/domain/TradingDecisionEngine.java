package org.predictiveedge.decision.domain;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Conservative, deterministic coordinator for the six governed intelligence authorities. */
@Deprecated(forRemoval = true)
public final class TradingDecisionEngine {
    private static final Set<IntelligenceModule> REQUIRED = Set.of(IntelligenceModule.values());
    private static final Set<IntelligenceModule> DIRECTION_REQUIRED = Set.of(
            IntelligenceModule.CHART, IntelligenceModule.STRATEGY, IntelligenceModule.DECISION);
    private static final Set<IntelligenceModule> HARD_GATES = Set.of(
            IntelligenceModule.RISK, IntelligenceModule.PORTFOLIO);

    public TradingRecommendation evaluate(
            String recommendationId,
            TraderIntent intent,
            List<IntelligenceFeedback> feedback,
            Instant evaluatedAt) {
        Objects.requireNonNull(intent, "Trader intent is required");
        Objects.requireNonNull(feedback, "Intelligence feedback is required");
        Objects.requireNonNull(evaluatedAt, "Evaluation time is required");
        Map<IntelligenceModule, IntelligenceFeedback> byModule = index(intent, feedback);
        List<String> feedbackRefs = byModule.values().stream()
                .sorted(Comparator.comparing(IntelligenceFeedback::module))
                .map(IntelligenceFeedback::feedbackId).toList();
        String manifestHash = manifestHash(intent, byModule);

        if (!intent.isActiveAt(evaluatedAt)) {
            return outcome(recommendationId, intent, RecommendationAction.NO_TRADE, evaluatedAt,
                    DecisionReason.TRADER_INTENT_NOT_ACTIVE, List.of(), feedbackRefs, manifestHash, 0);
        }

        List<IntelligenceModule> missing = REQUIRED.stream().filter(module -> !byModule.containsKey(module)).sorted().toList();
        if (!missing.isEmpty()) {
            return outcome(recommendationId, intent, RecommendationAction.INSUFFICIENT_EVIDENCE, evaluatedAt,
                    DecisionReason.MISSING_INTELLIGENCE_FEEDBACK, missing, feedbackRefs, manifestHash, 0);
        }

        List<IntelligenceModule> unusable = byModule.values().stream()
                .filter(value -> !value.isUsableAt(evaluatedAt))
                .map(IntelligenceFeedback::module).sorted().toList();
        if (!unusable.isEmpty()) {
            return outcome(recommendationId, intent, RecommendationAction.INSUFFICIENT_EVIDENCE, evaluatedAt,
                    DecisionReason.INTELLIGENCE_NOT_READY, unusable, feedbackRefs, manifestHash, 0);
        }

        List<IntelligenceModule> vetoes = byModule.values().stream()
                .filter(value -> value.gateDisposition() == GateDisposition.VETO)
                .map(IntelligenceFeedback::module).sorted().toList();
        if (!vetoes.isEmpty()) {
            return outcome(recommendationId, intent, RecommendationAction.NO_TRADE, evaluatedAt,
                    DecisionReason.INTELLIGENCE_VETO, vetoes, feedbackRefs, manifestHash, 0);
        }

        List<IntelligenceModule> incompleteGates = HARD_GATES.stream()
                .filter(module -> byModule.get(module).gateDisposition() == GateDisposition.NOT_APPLICABLE)
                .sorted().toList();
        if (!incompleteGates.isEmpty()) {
            return outcome(recommendationId, intent, RecommendationAction.INSUFFICIENT_EVIDENCE, evaluatedAt,
                    DecisionReason.INTELLIGENCE_NOT_READY, incompleteGates, feedbackRefs, manifestHash, 0);
        }

        RecommendationAction candidate = byModule.get(IntelligenceModule.DECISION).proposedAction();
        if (!candidate.isDirectional()) {
            return outcome(recommendationId, intent, RecommendationAction.WAIT, evaluatedAt,
                    DecisionReason.DECISION_ABSTAINED, List.of(IntelligenceModule.DECISION),
                    feedbackRefs, manifestHash, 0);
        }
        if (!intent.permittedActions().contains(candidate)) {
            return outcome(recommendationId, intent, RecommendationAction.NO_TRADE, evaluatedAt,
                    DecisionReason.DIRECTION_NOT_PERMITTED, List.of(IntelligenceModule.DECISION),
                    feedbackRefs, manifestHash, 0);
        }

        List<IntelligenceModule> conflicts = byModule.values().stream()
                .filter(value -> value.proposedAction().isDirectional() && value.proposedAction() != candidate)
                .map(IntelligenceFeedback::module).sorted().toList();
        List<IntelligenceModule> requiredAbstentions = DIRECTION_REQUIRED.stream()
                .filter(module -> byModule.get(module).proposedAction() == RecommendationAction.WAIT).sorted().toList();
        if (!conflicts.isEmpty() || !requiredAbstentions.isEmpty()) {
            List<IntelligenceModule> blockers = new ArrayList<>(conflicts);
            requiredAbstentions.stream().filter(module -> !blockers.contains(module)).forEach(blockers::add);
            blockers.sort(Comparator.naturalOrder());
            return outcome(recommendationId, intent, RecommendationAction.WAIT, evaluatedAt,
                    DecisionReason.DIRECTION_CONFLICT, blockers, feedbackRefs, manifestHash, 0);
        }

        int confidence = DIRECTION_REQUIRED.stream().map(byModule::get)
                .mapToInt(IntelligenceFeedback::confidence).min().orElse(0);
        IntelligenceFeedback scanner = byModule.get(IntelligenceModule.SCANNER);
        if (scanner.proposedAction() == candidate) confidence = Math.min(confidence, scanner.confidence());
        return outcome(recommendationId, intent, candidate, evaluatedAt,
                DecisionReason.RECOMMENDATION_APPROVED, List.of(), feedbackRefs, manifestHash, confidence);
    }

    private static Map<IntelligenceModule, IntelligenceFeedback> index(
            TraderIntent intent, List<IntelligenceFeedback> feedback) {
        Map<IntelligenceModule, IntelligenceFeedback> indexed = new EnumMap<>(IntelligenceModule.class);
        for (IntelligenceFeedback value : feedback) {
            Objects.requireNonNull(value, "Intelligence feedback cannot contain null");
            if (!value.instrument().equals(intent.instrument())) {
                throw new IllegalArgumentException("All feedback must reference the trader-intent instrument");
            }
            if (indexed.putIfAbsent(value.module(), value) != null) {
                throw new IllegalArgumentException("Duplicate feedback for module " + value.module());
            }
        }
        return indexed;
    }

    private static TradingRecommendation outcome(String recommendationId, TraderIntent intent,
            RecommendationAction action, Instant evaluatedAt, DecisionReason reason,
            List<IntelligenceModule> blockers, List<String> feedbackRefs, String manifestHash, int confidence) {
        return new TradingRecommendation(recommendationId, intent.intentId(), intent.instrument(), action,
                confidence, evaluatedAt, reason, blockers, feedbackRefs, manifestHash);
    }

    private static String manifestHash(TraderIntent intent, Map<IntelligenceModule, IntelligenceFeedback> feedback) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            add(digest, intent.intentId());
            feedback.values().stream().sorted(Comparator.comparing(IntelligenceFeedback::module)).forEach(value -> {
                add(digest, value.module().name());
                add(digest, value.feedbackId());
                add(digest, value.proposedAction().name());
                add(digest, Integer.toString(value.confidence()));
                add(digest, value.readiness().name());
                add(digest, value.gateDisposition().name());
                add(digest, Boolean.toString(value.finalEvidence()));
                add(digest, value.analysisCutoff().toString());
                add(digest, value.knowledgeCutoff().toString());
                add(digest, value.availableAt().toString());
                add(digest, value.validUntil().toString());
                add(digest, value.inputManifestHash());
                value.reasons().forEach(reason -> add(digest, reason));
                value.evidenceReferences().forEach(reference -> add(digest, reference));
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void add(MessageDigest digest, String value) {
        digest.update(Integer.toString(value.length()).getBytes(UTF_8));
        digest.update((byte) ':');
        digest.update(value.getBytes(UTF_8));
    }
}
