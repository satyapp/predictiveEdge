package org.predictiveedge.marketintelligence.api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.predictiveedge.marketintelligence.application.MarketBarQueryService;
import org.predictiveedge.marketintelligence.application.MarketBarReplayCriteria;
import org.predictiveedge.marketintelligence.application.MarketBarReplayCursor;
import org.predictiveedge.marketintelligence.domain.BarFinalityState;
import org.predictiveedge.marketintelligence.domain.BarTimeframe;
import org.predictiveedge.marketintelligence.domain.EvaluationCutoff;
import org.predictiveedge.marketintelligence.domain.MarketBarRevision;
import org.predictiveedge.marketintelligence.domain.ObservationSubject;
import org.predictiveedge.marketintelligence.domain.ObservationSubjectType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-intelligence/v1/bars")
public class MarketBarQueryController {
    private final MarketBarQueryService bars;

    public MarketBarQueryController(MarketBarQueryService bars) {
        this.bars = bars;
    }

    @GetMapping("/latest")
    public ResponseEntity<MarketBarResponse> latest(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam String brokerAccountId,
            @RequestParam SubjectKind subjectType,
            @RequestParam String exchange,
            @RequestParam String symbol,
            @RequestParam BarTimeframe timeframe,
            @RequestParam Instant analysisCutoff,
            @RequestParam Instant knowledgeCutoff) {
        var query = new MarketBarQueryService.LatestQuery(identity.user().id(), brokerAccountId,
                subject(subjectType, exchange, symbol), timeframe,
                new EvaluationCutoff(analysisCutoff, knowledgeCutoff));
        return bars.latest(query).map(MarketBarResponse::from).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/replay")
    public ReplayResponse replay(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam String brokerAccountId,
            @RequestParam SubjectKind subjectType,
            @RequestParam String exchange,
            @RequestParam String symbol,
            @RequestParam BarTimeframe timeframe,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam Instant analysisCutoff,
            @RequestParam Instant knowledgeCutoff,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "500") int limit) {
        var criteria = new MarketBarReplayCriteria(identity.user().id(), brokerAccountId,
                subject(subjectType, exchange, symbol), timeframe, from, to,
                new EvaluationCutoff(analysisCutoff, knowledgeCutoff), CursorCodec.decode(cursor));
        var page = bars.replay(criteria, limit);
        return new ReplayResponse(page.bars().stream().map(MarketBarResponse::from).toList(),
                CursorCodec.encode(page.next()), analysisCutoff, knowledgeCutoff);
    }

    private static ObservationSubject subject(SubjectKind type, String exchange, String symbol) {
        var instrument = new Instrument(exchange, symbol);
        return new ObservationSubject(ObservationSubjectType.valueOf(type.name()),
                instrument.exchange() + ":" + instrument.symbol());
    }

    public enum SubjectKind { INSTRUMENT, INDEX }

    public record ReplayResponse(
            List<MarketBarResponse> bars,
            String nextCursor,
            Instant analysisCutoff,
            Instant knowledgeCutoff) {}

    public record MarketBarResponse(
            String subjectType,
            String subjectId,
            String venue,
            LocalDate tradingDate,
            String sessionCode,
            BarTimeframe timeframe,
            Instant intervalStart,
            Instant intervalEnd,
            boolean truncatedBySessionEnd,
            long revision,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume,
            Instant observedThrough,
            BarFinalityState finalityState,
            Instant availableAt,
            String correctionReason,
            String inputManifestHash,
            String aggregationPolicyVersion,
            String finalityPolicyVersion) {
        static MarketBarResponse from(MarketBarRevision value) {
            var key = value.key();
            var session = key.sessionId();
            var prices = value.values();
            return new MarketBarResponse(key.subject().type().name(), key.subject().id(), session.venue(),
                    session.tradingDate(), session.sessionCode(), key.timeframe(), key.interval().startsAt(),
                    key.interval().endsAt(), key.interval().truncatedBySessionEnd(), value.revision(), prices.open(),
                    prices.high(), prices.low(), prices.close(), prices.volume(), value.observedThrough(),
                    value.finalityState(), value.availableAt(), value.correctionReason(),
                    value.inputManifestHash().value(), value.aggregationPolicyVersion(),
                    value.finalityPolicyVersion());
        }
    }

    static final class CursorCodec {
        private CursorCodec() {}

        static String encode(MarketBarReplayCursor cursor) {
            if (cursor == null) return null;
            try {
                var bytes = new ByteArrayOutputStream();
                try (var output = new DataOutputStream(bytes)) {
                    output.writeLong(cursor.intervalStart().getEpochSecond());
                    output.writeInt(cursor.intervalStart().getNano());
                    output.writeUTF(cursor.venue());
                    output.writeLong(cursor.tradingDate().toEpochDay());
                    output.writeUTF(cursor.sessionCode());
                }
                return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
            } catch (IOException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        static MarketBarReplayCursor decode(String encoded) {
            if (encoded == null || encoded.isBlank()) return null;
            try {
                byte[] bytes = Base64.getUrlDecoder().decode(encoded.getBytes(StandardCharsets.US_ASCII));
                try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                    var cursor = new MarketBarReplayCursor(
                            Instant.ofEpochSecond(input.readLong(), input.readInt()), input.readUTF(),
                            LocalDate.ofEpochDay(input.readLong()), input.readUTF());
                    if (input.available() != 0) throw new IllegalArgumentException("Replay cursor has trailing data");
                    return cursor;
                }
            } catch (IOException | RuntimeException failure) {
                throw new IllegalArgumentException("Replay cursor is invalid", failure);
            }
        }
    }
}
