package org.predictiveedge.marketintelligence.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.predictiveedge.broker.connection.UserMarketDataSubscriptionStatus;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.MarketDataDetail;
import org.predictiveedge.broker.domain.MarketDataInstrumentKind;
import org.predictiveedge.broker.domain.MarketDataStreamState;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.predictiveedge.marketintelligence.application.UserMarketIntelligenceSubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-intelligence/v1/subscription")
public class MarketIntelligenceSubscriptionController {
    private final UserMarketIntelligenceSubscriptionService subscriptions;

    public MarketIntelligenceSubscriptionController(UserMarketIntelligenceSubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    /** Creates or idempotently replaces the authenticated user's singleton live subscription. */
    @PutMapping
    public SubscriptionResponse subscribe(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody SubscriptionRequest request) {
        var context = BrokerContext.withoutCredentials(identity.user().id(), request.brokerAccountId().trim());
        var instruments = request.instruments().stream()
                .map(value -> new Instrument(value.exchange(), value.symbol())).toList();
        return SubscriptionResponse.from(subscriptions.subscribe(context, instruments));
    }

    @GetMapping
    public ResponseEntity<SubscriptionResponse> status(
            @AuthenticationPrincipal AuthenticatedIdentity identity) {
        return subscriptions.status(identity.user().id())
                .map(SubscriptionResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Idempotently closes only the authenticated user's stream. */
    @DeleteMapping
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal AuthenticatedIdentity identity) {
        subscriptions.unsubscribe(identity.user().id());
        return ResponseEntity.noContent().build();
    }

    public record SubscriptionRequest(
            @NotBlank @Size(max = 128) String brokerAccountId,
            @NotEmpty @Size(max = 3_000) List<@NotNull @Valid InstrumentRequest> instruments) {}

    public record InstrumentRequest(
            @NotBlank @Size(max = 32) String exchange,
            @NotBlank @Size(max = 128) String symbol) {}

    public record SubscriptionResponse(
            String brokerAccountId,
            MarketDataStreamState state,
            MarketDataDetail detail,
            List<InstrumentResponse> instruments) {
        static SubscriptionResponse from(UserMarketDataSubscriptionStatus status) {
            return new SubscriptionResponse(status.brokerAccountId(), status.state(), status.subscription().detail(),
                    status.subscription().instruments().stream().map(value -> new InstrumentResponse(
                            value.instrument().exchange(), value.instrument().symbol(), value.kind())).toList());
        }
    }

    public record InstrumentResponse(String exchange, String symbol, MarketDataInstrumentKind kind) {}
}
