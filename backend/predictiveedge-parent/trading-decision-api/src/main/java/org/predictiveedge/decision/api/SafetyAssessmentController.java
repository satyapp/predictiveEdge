package org.predictiveedge.decision.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.decision.application.SafetyAssessmentRequest;
import org.predictiveedge.decision.application.SafetyAssessmentResult;
import org.predictiveedge.decision.application.SafetySnapshotProducer;
import org.predictiveedge.decision.domain.InstrumentRef;
import org.predictiveedge.decision.domain.RecommendationAction;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/decision/v1/safety-assessments")
@ConditionalOnBean(SafetySnapshotProducer.class)
public class SafetyAssessmentController {
    private final SafetySnapshotProducer producer;

    public SafetyAssessmentController(SafetySnapshotProducer producer) { this.producer = producer; }

    @PostMapping
    public SafetyAssessmentResult assess(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody Request request) {
        var assessment = new SafetyAssessmentRequest(identity.user().id(), request.brokerAccountId(),
                new InstrumentRef(request.venue(), request.instrumentId()),
                new Instrument(request.venue(), request.marketSymbol()), request.direction(), request.quantity(),
                request.stopLossPrice(), request.analysisCutoff(), request.knowledgeCutoff());
        return producer.assess(assessment);
    }

    public record Request(
            @NotBlank String brokerAccountId,
            @NotBlank String venue,
            @NotBlank String instrumentId,
            @NotBlank String marketSymbol,
            @NotNull RecommendationAction direction,
            @Positive int quantity,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal stopLossPrice,
            @NotNull Instant analysisCutoff,
            @NotNull Instant knowledgeCutoff) { }
}
