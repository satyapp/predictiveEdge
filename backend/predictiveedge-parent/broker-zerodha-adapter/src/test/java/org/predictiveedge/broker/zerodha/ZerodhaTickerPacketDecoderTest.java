package org.predictiveedge.broker.zerodha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.domain.BrokerFailure;
import org.predictiveedge.broker.domain.EquityMarketTick;
import org.predictiveedge.broker.domain.IndexMarketTick;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.LiveMarketDataInstrument;
import org.predictiveedge.broker.domain.LiveMarketDataSubscription;
import org.predictiveedge.broker.domain.MarketDataDetail;
import org.predictiveedge.broker.domain.MarketDataInstrumentKind;

class ZerodhaTickerPacketDecoderTest {
    private static final long EQUITY_TOKEN = 408065;
    private static final long INDEX_TOKEN = 264969;
    private static final Instant EXCHANGE_AT = Instant.parse("2026-08-05T10:00:00Z");
    private static final Instant RECEIVED_AT = EXCHANGE_AT.plusMillis(250);

    @Test
    void decodesEquityAndIndexFullPacketsFromOneFrame() {
        var decoder = new ZerodhaTickerPacketDecoder(subscription());

        var ticks = decoder.decode(frame(equityPacket(), indexPacket()), RECEIVED_AT);

        assertThat(ticks).hasSize(2);
        var equity = (EquityMarketTick) ticks.get(0);
        assertThat(equity.instrument()).isEqualTo(new Instrument("NSE", "INFY"));
        assertThat(equity.lastPrice()).isEqualByComparingTo("2500.50");
        assertThat(equity.cumulativeVolume()).isEqualTo(1_250_000);
        assertThat(equity.lastTradeTimestamp()).isEqualTo(EXCHANGE_AT.minusSeconds(1));
        assertThat(equity.exchangeTimestamp()).isEqualTo(EXCHANGE_AT);
        var index = (IndexMarketTick) ticks.get(1);
        assertThat(index.instrument()).isEqualTo(new Instrument("NSE", "INDIA VIX"));
        assertThat(index.lastPrice()).isEqualByComparingTo("13.75");
        assertThat(index.changePercent()).isEqualByComparingTo("-1.25");
    }

    @Test
    void ignoresSingleByteHeartbeat() {
        assertThat(new ZerodhaTickerPacketDecoder(subscription())
                .decode(ByteBuffer.wrap(new byte[] {0}), RECEIVED_AT)).isEmpty();
    }

    @Test
    void failsClosedForTruncatedOrUnknownPackets() {
        var decoder = new ZerodhaTickerPacketDecoder(subscription());
        var truncated = ByteBuffer.allocate(5).order(ByteOrder.BIG_ENDIAN)
                .putShort((short) 1).putShort((short) 184).put((byte) 0).flip();

        assertThatThrownBy(() -> decoder.decode(truncated, RECEIVED_AT))
                .isInstanceOf(BrokerFailure.class).hasMessageContaining("truncated");
        var unknown = equityPacket(); unknown.putInt(0, 999999); unknown.position(0);
        assertThatThrownBy(() -> decoder.decode(frame(unknown), RECEIVED_AT))
                .isInstanceOf(BrokerFailure.class).hasMessageContaining("unsubscribed");
    }

    private static LiveMarketDataSubscription subscription() {
        return new LiveMarketDataSubscription(List.of(
                new LiveMarketDataInstrument(new Instrument("NSE", "INFY"), Long.toString(EQUITY_TOKEN),
                        MarketDataInstrumentKind.EQUITY),
                new LiveMarketDataInstrument(new Instrument("NSE", "INDIA VIX"), Long.toString(INDEX_TOKEN),
                        MarketDataInstrumentKind.INDEX)), MarketDataDetail.FULL);
    }

    private static ByteBuffer equityPacket() {
        var value = ByteBuffer.allocate(184).order(ByteOrder.BIG_ENDIAN);
        value.putInt((int) EQUITY_TOKEN).putInt(250_050).putInt(25).putInt(249_900).putInt(1_250_000)
                .putInt(50_000).putInt(45_000).putInt(248_000).putInt(252_000).putInt(247_000).putInt(246_500)
                .putInt((int) EXCHANGE_AT.minusSeconds(1).getEpochSecond()).putInt(0).putInt(0).putInt(0)
                .putInt((int) EXCHANGE_AT.getEpochSecond());
        value.position(184); // Remaining bytes are the ten zero-valued market-depth entries.
        return value.flip();
    }

    private static ByteBuffer indexPacket() {
        var value = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN);
        value.putInt((int) INDEX_TOKEN).putInt(1_375).putInt(1_410).putInt(1_350).putInt(1_390).putInt(1_392)
                .putInt(-125).putInt((int) EXCHANGE_AT.getEpochSecond());
        return value.flip();
    }

    private static ByteBuffer frame(ByteBuffer... packets) {
        int size = 2;
        for (ByteBuffer packet : packets) size += 2 + packet.remaining();
        var frame = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN).putShort((short) packets.length);
        for (ByteBuffer packet : packets) {
            var copy = packet.asReadOnlyBuffer();
            frame.putShort((short) copy.remaining()).put(copy);
        }
        return frame.flip();
    }
}
