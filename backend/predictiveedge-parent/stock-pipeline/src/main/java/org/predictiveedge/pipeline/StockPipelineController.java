package org.predictiveedge.pipeline;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consolidated stock pipeline: level-1 qualified stocks from every saved run, and level-2 AI review. */
@RestController
@RequestMapping("/api/stock-pipeline/v1")
public class StockPipelineController {
    private static final int DEFAULT_RUNS = 40;
    private final QualifiedStockService stocks;
    private final LevelTwoAgent levelTwo;

    public StockPipelineController(QualifiedStockService stocks, LevelTwoAgent levelTwo) {
        this.stocks = stocks;
        this.levelTwo = levelTwo;
    }

    @GetMapping("/qualified-stocks")
    public Overview qualifiedStocks(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam(defaultValue = "40") int runs) {
        return new Overview(levelTwo.available(), levelTwo.model(), stocks.qualifiedStocks(identity.user().id(), runs));
    }

    @PostMapping("/qualified-stocks/{symbol}/level-two")
    public LevelTwoVerdict runLevelTwo(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable String symbol,
            @RequestParam(required = false) UUID runId) {
        UUID user = identity.user().id();
        var context = stocks.levelOneContext(user, symbol, runId, DEFAULT_RUNS)
                .orElseThrow(() -> new IllegalArgumentException("No saved level-1 analysis was found for " + symbol
                        + ". Run it through a screener first."));
        return levelTwo.analyze(user, context);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> notFound(IllegalArgumentException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "PIPELINE_NOT_FOUND", "message", failure.getMessage()));
    }

    @ExceptionHandler(LevelTwoAgent.LevelTwoUnavailableException.class)
    ResponseEntity<Map<String, String>> unavailable(LevelTwoAgent.LevelTwoUnavailableException failure) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("code", "LEVEL_TWO_UNAVAILABLE", "message", failure.getMessage()));
    }

    public record Overview(boolean levelTwoAvailable, String levelTwoModel, List<QualifiedStockService.QualifiedStock> stocks) { }
}
