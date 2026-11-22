package org.predictiveedge.fyers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tts.in.model.FyersClass;
import com.tts.in.model.PlaceOrderModel;
import com.tts.in.utilities.Tuple;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.predictiveedge.fyers.FyersOrderService.Confirmation;
import org.predictiveedge.fyers.FyersOrderService.OrderRequest;
import org.predictiveedge.fyers.FyersOrderService.Product;
import org.predictiveedge.fyers.FyersOrderService.Side;
import org.predictiveedge.fyers.FyersOrderService.Type;

class FyersSdkGatewayTest {
    private static final UUID USER = UUID.randomUUID();
    private final FyersClass sdk = mock(FyersClass.class);
    private final ObjectMapper json = new ObjectMapper();
    private final FyersSdkGateway gateway = new FyersSdkGateway(user -> "APP-100:token-" + user, () -> sdk, json, 1);

    @Test
    void installsTheUsersCredentialsOnlyForTheCallAndConvertsTheResponse() {
        AtomicReference<String> seen = new AtomicReference<>();
        when(sdk.GetProfile()).thenAnswer(call -> {
            seen.set(sdk.clientId + "|" + sdk.accessToken);
            return new Tuple<>(new JSONObject("{\"s\":\"ok\",\"data\":{\"name\":\"Demo\"}}"), null);
        });
        assertThat(gateway.profile(USER).path("data").path("name").asText()).isEqualTo("Demo");
        assertThat(seen.get()).isEqualTo("APP-100|token-" + USER);
        assertThat(sdk.clientId).isNull();
        assertThat(sdk.accessToken).isNull();
    }

    @Test
    void reportsFyersErrorsAndStillClearsCredentials() {
        when(sdk.GetFunds()).thenReturn(new Tuple<>(null, new JSONObject("{\"s\":\"error\",\"message\":\"Invalid token\"}")));
        assertThatThrownBy(() -> gateway.funds(USER)).isInstanceOf(FyersSdkGateway.FyersSdkException.class).hasMessage("Invalid token");
        assertThat(sdk.accessToken).isNull();
    }

    @Test
    void refusesWhenTheUserHasNoConnection() {
        var disconnected = new FyersSdkGateway(user -> null, () -> sdk, json, 1);
        assertThatThrownBy(() -> disconnected.positions(USER)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ordersAreOffByDefault() {
        var orders = new FyersOrderService(gateway, new FyersOrderService.Settings(false, 100, new BigDecimal("100000")));
        var order = new OrderRequest("NSE:SBIN-EQ", Side.BUY, Type.LIMIT, Product.INTRADAY, 10, new BigDecimal("800"), null, "pe");
        assertThatThrownBy(() -> orders.place(USER, order, new Confirmation("NSE:SBIN-EQ", Side.BUY, 10)))
                .isInstanceOf(FyersOrderService.OrdersDisabledException.class);
        verify(sdk, never()).PlaceOrder(any());
    }

    @Test
    void placesAConfirmedLimitOrderWithFyersCodes() {
        var orders = new FyersOrderService(gateway, new FyersOrderService.Settings(true, 100, new BigDecimal("100000")));
        when(sdk.PlaceOrder(any())).thenReturn(new Tuple<>(new JSONObject("{\"s\":\"ok\",\"id\":\"2611220001\"}"), null));
        var order = new OrderRequest("NSE:SBIN-EQ", Side.SELL, Type.LIMIT, Product.INTRADAY, 10, new BigDecimal("812.5"), null, "pe-1");
        assertThat(orders.place(USER, order, new Confirmation("NSE:SBIN-EQ", Side.SELL, 10)).path("id").asText()).isEqualTo("2611220001");
        ArgumentCaptor<PlaceOrderModel> sent = ArgumentCaptor.forClass(PlaceOrderModel.class);
        verify(sdk).PlaceOrder(sent.capture());
        assertThat(sent.getValue().Side).isEqualTo(-1);
        assertThat(sent.getValue().OrderType).isEqualTo(1);
        assertThat(sent.getValue().ProductType).isEqualTo("INTRADAY");
        assertThat(sent.getValue().LimitPrice).isEqualTo(812.5);
        assertThat(sent.getValue().OrderTag).isEqualTo("pe1");
    }

    @Test
    void rejectsUnconfirmedOversizedOrMarketOrders() {
        var orders = new FyersOrderService(gateway, new FyersOrderService.Settings(true, 100, new BigDecimal("100000")));
        var order = new OrderRequest("NSE:SBIN-EQ", Side.BUY, Type.LIMIT, Product.INTRADAY, 10, new BigDecimal("800"), null, "");
        assertThatThrownBy(() -> orders.place(USER, order, new Confirmation("NSE:SBIN-EQ", Side.BUY, 11)))
                .hasMessageContaining("confirmed");
        assertThatThrownBy(() -> orders.place(USER, new OrderRequest("NSE:SBIN-EQ", Side.BUY, Type.LIMIT, Product.INTRADAY, 100,
                new BigDecimal("1200"), null, ""), new Confirmation("NSE:SBIN-EQ", Side.BUY, 100))).hasMessageContaining("exceeds the cap");
        assertThatThrownBy(() -> orders.place(USER, new OrderRequest("NSE:SBIN-EQ", Side.BUY, Type.MARKET, Product.INTRADAY, 1,
                null, null, ""), new Confirmation("NSE:SBIN-EQ", Side.BUY, 1))).hasMessageContaining("Market orders");
        assertThatThrownBy(() -> orders.place(USER, new OrderRequest("NSE:NIFTY25DECFUT", Side.BUY, Type.LIMIT, Product.MARGIN, 1,
                new BigDecimal("1"), null, ""), new Confirmation("NSE:NIFTY25DECFUT", Side.BUY, 1))).hasMessageContaining("cash equities");
        verify(sdk, never()).PlaceOrder(any());
    }
}
