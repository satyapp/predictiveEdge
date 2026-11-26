package org.predictiveedge.chart.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.predictiveedge.chart.application.IntradayListRecorder;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Saves the user's intraday list: stocks of one analysed search that were added to today's list. */
@RestController
@RequestMapping("/api/chart-intelligence/v1/intraday-lists")
@ConditionalOnBean(IntradayListRecorder.class)
public class IntradayListController {
    private final IntradayListRecorder lists;

    public IntradayListController(IntradayListRecorder lists) {
        this.lists = lists;
    }

    @PostMapping
    public Response save(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestBody Request request) {
        UUID listId = lists.record(identity.user().id(), request.searchId(), request.symbols(), Instant.now());
        return new Response(listId, request.searchId(), request.symbols().stream()
                .map(value -> value.trim().toUpperCase(java.util.Locale.ROOT)).distinct().toList());
    }

    public record Request(@NotNull UUID searchId, @NotEmpty List<String> symbols) { }

    public record Response(UUID listId, UUID searchId, List<String> symbols) { }
}
