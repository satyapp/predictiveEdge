package org.predictiveedge.fyers;

import com.fasterxml.jackson.databind.JsonNode;
import com.tts.in.model.PlaceOrderModel;
import com.tts.in.utilities.OrderType;
import com.tts.in.utilities.OrderValidity;
import com.tts.in.utilities.ProductType;
import com.tts.in.utilities.TransactionType;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Places, modifies and cancels FYERS orders through the SDK (see ADR-0006).
 *
 * <p>Guardrails: off unless {@code predictiveedge.fyers.orders.enabled=true}; every call must carry the user's
 * explicit confirmation of the exact symbol, side and quantity; quantity and notional value are capped; NSE/BSE
 * cash equities only; limit and stop-limit orders only, so every order has a price for the value cap. No code path calls this automatically: there is no scheduler, AI or Guardian caller.
 */
public final class FyersOrderService {
    private static final Logger log = LoggerFactory.getLogger(FyersOrderService.class);
    private static final Pattern SYMBOL = Pattern.compile("(NSE|BSE):[A-Z0-9&_.-]+-EQ");

    public enum Side { BUY, SELL }
    public enum Type { LIMIT, MARKET, STOP_MARKET, STOP_LIMIT }
    public enum Product { INTRADAY, CNC, MARGIN }

    public record Settings(boolean enabled, int maximumQuantity, BigDecimal maximumOrderValue) { }

    /** What the user typed or tapped to confirm; must match the order exactly. */
    public record Confirmation(String symbol, Side side, int quantity) { }

    public record OrderRequest(String symbol, Side side, Type type, Product product, int quantity,
            BigDecimal limitPrice, BigDecimal stopPrice, String tag) { }

    private final FyersSdkGateway gateway;
    private final Settings settings;

    public FyersOrderService(FyersSdkGateway gateway, Settings settings) {
        this.gateway = Objects.requireNonNull(gateway);
        this.settings = Objects.requireNonNull(settings);
    }

    public boolean enabled() { return settings.enabled(); }

    public JsonNode place(UUID userId, OrderRequest order, Confirmation confirmation) {
        requireEnabled();
        validate(order);
        requireConfirmation(order, confirmation);
        PlaceOrderModel model = new PlaceOrderModel();
        model.Symbol = order.symbol();
        model.Qty = order.quantity();
        model.Side = (order.side() == Side.BUY ? TransactionType.Buy : TransactionType.Sell).getValue();
        model.OrderType = orderType(order.type()).getDescription();
        model.ProductType = switch (order.product()) {
            case INTRADAY -> ProductType.INTRADAY; case CNC -> ProductType.CNC; case MARGIN -> ProductType.MARGIN; };
        model.OrderValidity = OrderValidity.DAY;
        model.LimitPrice = order.limitPrice() == null ? 0 : order.limitPrice().doubleValue();
        model.StopPrice = order.stopPrice() == null ? 0 : order.stopPrice().doubleValue();
        model.OrderTag = order.tag() == null ? "" : order.tag().replaceAll("[^A-Za-z0-9]", "");
        log.info("FYERS order requested user={} symbol={} side={} qty={} type={}", userId, order.symbol(), order.side(), order.quantity(), order.type());
        JsonNode result = gateway.call(userId, fyers -> fyers.PlaceOrder(model));
        log.info("FYERS order accepted user={} symbol={} id={}", userId, order.symbol(), result.path("id").asText(""));
        return result;
    }

    public JsonNode cancel(UUID userId, String orderId) {
        requireEnabled();
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("Order id is required");
        log.info("FYERS cancel requested user={} order={}", userId, orderId);
        return gateway.call(userId, fyers -> fyers.CancelOrder(orderId));
    }

    private void requireEnabled() {
        if (!settings.enabled()) throw new OrdersDisabledException();
    }

    void validate(OrderRequest order) {
        if (order == null) throw new IllegalArgumentException("Order is required");
        String symbol = Objects.requireNonNullElse(order.symbol(), "").toUpperCase(Locale.ROOT);
        if (!SYMBOL.matcher(symbol).matches() || !symbol.equals(order.symbol()))
            throw new IllegalArgumentException("Only NSE/BSE cash equities are allowed, e.g. NSE:SBIN-EQ");
        if (order.side() == null || order.type() == null || order.product() == null)
            throw new IllegalArgumentException("Side, order type and product are required");
        if (order.quantity() <= 0 || order.quantity() > settings.maximumQuantity())
            throw new IllegalArgumentException("Quantity must be between 1 and " + settings.maximumQuantity());
        boolean needsLimit = order.type() == Type.LIMIT || order.type() == Type.STOP_LIMIT;
        boolean needsStop = order.type() == Type.STOP_MARKET || order.type() == Type.STOP_LIMIT;
        if (needsLimit && !positive(order.limitPrice())) throw new IllegalArgumentException("A limit price is required");
        if (needsStop && !positive(order.stopPrice())) throw new IllegalArgumentException("A stop (trigger) price is required");
        if (order.type() == Type.MARKET || order.type() == Type.STOP_MARKET)
            throw new IllegalArgumentException("Market orders are not allowed yet: the value cap needs a price. Use a limit order");
        BigDecimal reference = order.limitPrice();
        BigDecimal value = reference.multiply(BigDecimal.valueOf(order.quantity()));
        if (value.compareTo(settings.maximumOrderValue()) > 0)
            throw new IllegalArgumentException("Order value " + value + " exceeds the cap of " + settings.maximumOrderValue());
    }

    private static void requireConfirmation(OrderRequest order, Confirmation confirmation) {
        if (confirmation == null || !order.symbol().equals(confirmation.symbol()) || order.side() != confirmation.side()
                || order.quantity() != confirmation.quantity())
            throw new IllegalArgumentException("The order must be confirmed by the user with the same symbol, side and quantity");
    }

    private static OrderType orderType(Type type) {
        return switch (type) { case LIMIT -> OrderType.LimitOrder; case MARKET -> OrderType.MarketOrder;
            case STOP_MARKET -> OrderType.SLM; case STOP_LIMIT -> OrderType.SLL; };
    }

    private static boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }

    public static final class OrdersDisabledException extends RuntimeException {
        public OrdersDisabledException() { super("FYERS order placement is turned off (predictiveedge.fyers.orders.enabled=false)"); }
    }
}
