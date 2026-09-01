package org.predictiveedge.broker.api;

import java.net.URI;
import java.util.Map;

import org.predictiveedge.broker.connection.BrokerConnectionService;
import org.predictiveedge.broker.connection.BrokerAccountSnapshotService;
import org.predictiveedge.broker.connection.BrokerConnectionFailure;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/broker/v1")
public class BrokerConnectionController {
    private final BrokerConnectionService connections;
    private final BrokerAccountSnapshotService accountSnapshots;

    public BrokerConnectionController(
            BrokerConnectionService connections, BrokerAccountSnapshotService accountSnapshots) {
        this.connections = connections;
        this.accountSnapshots = accountSnapshots;
    }

    @GetMapping("/connections")
    public BrokerConnectionService.ConnectionOverview connections(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestHeader("Authorization") String predictiveEdgeSession) {
        return connections.overview(identity.user().id(), predictiveEdgeSession);
    }

    @PostMapping("/zerodha/connect")
    public Map<String, String> connect(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestHeader("Authorization") String predictiveEdgeSession) {
        return Map.of("authorizationUrl", connections.beginZerodhaConnection(
                identity.user().id(), predictiveEdgeSession).toString());
    }

    @PostMapping("/zerodha/lease/release")
    public ResponseEntity<Void> releaseBrowserLease(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestHeader("Authorization") String predictiveEdgeSession) {
        connections.signalBrowserClosing(identity.user().id(), predictiveEdgeSession);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/zerodha/connection")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal AuthenticatedIdentity identity) {
        connections.disconnectZerodha(identity.user().id());
        return ResponseEntity.noContent().build();
    }

    /** Explicit read-only probe used by the single-user shadow workflow. */
    @PostMapping("/zerodha/account-snapshots")
    public AccountSnapshotResponse captureAccountSnapshot(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestHeader("Authorization") String predictiveEdgeSession) {
        var overview = connections.overview(identity.user().id(), predictiveEdgeSession);
        if (!overview.zerodhaConnected() || overview.zerodhaAccountId() == null) {
            throw new BrokerConnectionFailure(BrokerConnectionFailure.Code.NOT_CONNECTED,
                    "Connect Zerodha before capturing account evidence");
        }
        var evidence = accountSnapshots.capture(BrokerContext.withoutCredentials(
                identity.user().id(), overview.zerodhaAccountId()));
        var snapshot = evidence.snapshot();
        return new AccountSnapshotResponse(evidence.snapshotId().toString(), snapshot.accountId(),
                snapshot.observedAt().toString(), snapshot.receivedAt().toString(), evidence.evidenceHash(),
                snapshot.funds().keySet(), snapshot.netPositions().size(), snapshot.dayPositions().size(),
                snapshot.holdings().size());
    }

    @GetMapping("/zerodha/callback")
    public ResponseEntity<Void> callback(
            @RequestParam("state") String state,
            @RequestParam("request_token") String requestToken) {
        URI redirect = connections.completeZerodhaConnection(state, requestToken);
        return ResponseEntity.status(302).location(redirect).build();
    }

    public record AccountSnapshotResponse(
            String snapshotId,
            String brokerAccountId,
            String observedAt,
            String receivedAt,
            String evidenceHash,
            java.util.Set<String> fundSegments,
            int netPositionCount,
            int dayPositionCount,
            int holdingCount) { }
}
