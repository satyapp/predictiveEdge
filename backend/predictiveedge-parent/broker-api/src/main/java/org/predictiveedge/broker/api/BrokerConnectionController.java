package org.predictiveedge.broker.api;

import java.net.URI;
import java.util.Map;

import org.predictiveedge.broker.connection.BrokerConnectionService;
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

    public BrokerConnectionController(BrokerConnectionService connections) { this.connections = connections; }

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

    @GetMapping("/zerodha/callback")
    public ResponseEntity<Void> callback(
            @RequestParam("state") String state,
            @RequestParam("request_token") String requestToken) {
        URI redirect = connections.completeZerodhaConnection(state, requestToken);
        return ResponseEntity.status(302).location(redirect).build();
    }
}
