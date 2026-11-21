package org.predictiveedge.fyers;

import java.net.URI;
import java.util.Map;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fyers/v1")
public class FyersConnectionController {
    private final FyersConnectionService connections;
    public FyersConnectionController(FyersConnectionService connections) { this.connections = connections; }
    @GetMapping("/connection")
    public FyersConnectionService.Status status(@AuthenticationPrincipal AuthenticatedIdentity identity) {
        return connections.status(identity.user().id());
    }
    @GetMapping("/verification")
    public FyersConnectionService.Verification verify(@AuthenticationPrincipal AuthenticatedIdentity identity) {
        return connections.verifyDataPath(identity.user().id());
    }
    @PostMapping("/connect")
    public Map<String, String> connect(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestHeader("Authorization") String session) {
        return Map.of("authorizationUrl", connections.begin(identity.user().id(), session).toString());
    }
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("state") String state,
            @RequestParam(name = "auth_code", required = false) String authCode,
            @RequestParam(name = "code", required = false) String code) {
        String value = authCode == null || authCode.isBlank() ? code : authCode;
        URI redirect = connections.complete(state, value);
        return ResponseEntity.status(302).location(redirect).build();
    }
    @DeleteMapping("/connection")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal AuthenticatedIdentity identity) {
        connections.disconnect(identity.user().id()); return ResponseEntity.noContent().build();
    }
}
