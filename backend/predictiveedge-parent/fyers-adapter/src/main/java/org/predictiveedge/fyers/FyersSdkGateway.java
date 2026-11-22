package org.predictiveedge.fyers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tts.in.model.FyersClass;
import com.tts.in.model.ScreenersStandardModel;
import com.tts.in.model.StockHistoryModel;
import com.tts.in.utilities.Tuple;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import org.json.JSONObject;

/**
 * Per-user access to the official FYERS Java SDK.
 *
 * <p>The SDK keeps one process-wide {@link FyersClass} singleton with public {@code clientId} and
 * {@code accessToken} fields. PredictiveEdge serves many users, each with their own FYERS token, so every call
 * here takes a lock, installs that user's credentials, makes the call, and clears the credentials again. Nothing
 * else in the application may touch {@code FyersClass} directly.
 */
public final class FyersSdkGateway {
    /** Supplies {@code "appId:accessToken"} for a user, as stored by {@link FyersConnectionService}. */
    @FunctionalInterface
    public interface CredentialSource { String authorization(UUID userId); }

    private final CredentialSource credentials;
    private final Supplier<FyersClass> sdk;
    private final ObjectMapper json;
    private final long lockTimeoutSeconds;
    private final ReentrantLock lock = new ReentrantLock(true);

    public FyersSdkGateway(CredentialSource credentials, Supplier<FyersClass> sdk, ObjectMapper json, long lockTimeoutSeconds) {
        this.credentials = Objects.requireNonNull(credentials);
        this.sdk = Objects.requireNonNull(sdk);
        this.json = Objects.requireNonNull(json);
        this.lockTimeoutSeconds = lockTimeoutSeconds;
    }

    // Account (read-only)
    public JsonNode profile(UUID userId) { return call(userId, FyersClass::GetProfile); }
    public JsonNode funds(UUID userId) { return call(userId, FyersClass::GetFunds); }
    public JsonNode holdings(UUID userId) { return call(userId, FyersClass::GetHoldings); }
    public JsonNode positions(UUID userId) { return call(userId, FyersClass::GetPositions); }
    public JsonNode orderBook(UUID userId) { return call(userId, FyersClass::GetAllOrders); }
    public JsonNode tradeBook(UUID userId) { return call(userId, FyersClass::GetTradeBook); }

    // Market data
    public JsonNode marketStatus(UUID userId) { return call(userId, FyersClass::GetMarketStatus); }
    public JsonNode quotes(UUID userId, List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) throw new IllegalArgumentException("At least one symbol is required");
        return call(userId, fyers -> fyers.GetStockQuotes(String.join(",", symbols)));
    }
    public JsonNode marketDepth(UUID userId, String symbol) { return call(userId, fyers -> fyers.GetMarketDepth(symbol, 1)); }
    public JsonNode history(UUID userId, String symbol, String resolution, String fromDate, String toDate) {
        StockHistoryModel model = new StockHistoryModel();
        model.Symbol = symbol; model.Resolution = resolution; model.DateFormat = "1";
        model.RangeFrom = fromDate; model.RangeTo = toDate; model.ContFlag = 1;
        return call(userId, fyers -> fyers.GetStockHistory(model));
    }
    public JsonNode optionChain(UUID userId, String symbol, int strikeCount) {
        return call(userId, fyers -> fyers.GetOptionChain(symbol, strikeCount, "", ""));
    }

    // Screeners
    public JsonNode screenersConfig(UUID userId) { return call(userId, FyersClass::GetScreenersConfig); }
    public JsonNode standardScreener(UUID userId, String screenerId, String universe, String timeframe) {
        ScreenersStandardModel model = new ScreenersStandardModel();
        model.ScreenerId = screenerId; model.Universe = universe; model.Timeframe = timeframe;
        return call(userId, fyers -> fyers.GetScreenersStandard(model));
    }

    /** Runs one SDK call with the user's credentials installed. Used by {@link FyersOrderService} for order calls. */
    JsonNode call(UUID userId, Function<FyersClass, Tuple<JSONObject, JSONObject>> operation) {
        if (userId == null) throw new IllegalArgumentException("User identity is required for FYERS");
        String authorization = credentials.authorization(userId);
        int split = authorization == null ? -1 : authorization.indexOf(':');
        if (split <= 0 || split == authorization.length() - 1) throw new IllegalStateException("FYERS is not connected for this user");
        boolean locked;
        try { locked = lock.tryLock(lockTimeoutSeconds, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new FyersSdkException("Interrupted while waiting for FYERS"); }
        if (!locked) throw new FyersSdkException("FYERS is busy; try again");
        FyersClass fyers = sdk.get();
        try {
            fyers.clientId = authorization.substring(0, split);
            fyers.accessToken = authorization.substring(split + 1);
            Tuple<JSONObject, JSONObject> result = operation.apply(fyers);
            return read(result);
        } catch (FyersSdkException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new FyersSdkException("FYERS SDK call failed: " + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
        } finally {
            fyers.clientId = null;
            fyers.accessToken = null;
            lock.unlock();
        }
    }

    private JsonNode read(Tuple<JSONObject, JSONObject> result) {
        if (result == null) throw new FyersSdkException("FYERS returned no response");
        JSONObject error = result.Item2();
        if (error != null && !error.isEmpty()) {
            String message = error.optString("message", "");
            throw new FyersSdkException(message.isBlank() ? "FYERS rejected the request" : message);
        }
        JSONObject body = result.Item1();
        if (body == null) throw new FyersSdkException("FYERS returned no response");
        try {
            JsonNode node = json.readTree(body.toString());
            if ("error".equalsIgnoreCase(node.path("s").asText())) {
                throw new FyersSdkException(node.path("message").asText("FYERS rejected the request"));
            }
            return node;
        } catch (JsonProcessingException failure) {
            throw new FyersSdkException("FYERS returned unreadable JSON");
        }
    }

    public static final class FyersSdkException extends RuntimeException {
        public FyersSdkException(String message) { super(message); }
    }
}
