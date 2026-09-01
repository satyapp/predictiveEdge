package org.predictiveedge.broker.zerodha;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.predictiveedge.broker.domain.BrokerFailure;
import org.predictiveedge.broker.domain.EquityMarketTick;
import org.predictiveedge.broker.domain.IndexMarketTick;
import org.predictiveedge.broker.domain.LiveMarketDataInstrument;
import org.predictiveedge.broker.domain.LiveMarketDataSubscription;
import org.predictiveedge.broker.domain.MarketDataDetail;
import org.predictiveedge.broker.domain.MarketDataInstrumentKind;
import org.predictiveedge.broker.domain.MarketDepthLevel;
import org.predictiveedge.broker.domain.MarketTick;

/** Strict decoder for the Kite Connect v3 big-endian binary quote protocol. */
public final class ZerodhaTickerPacketDecoder {
    private static final int EQUITY_FULL_BYTES = 184;
    private static final int INDEX_FULL_BYTES = 32;
    private static final BigDecimal PRICE_DIVISOR = BigDecimal.valueOf(100);

    private final Map<Long, LiveMarketDataInstrument> instrumentsByToken;

    public ZerodhaTickerPacketDecoder(LiveMarketDataSubscription subscription) {
        Objects.requireNonNull(subscription, "Subscription is required");
        if (subscription.detail() != MarketDataDetail.FULL)
            throw new IllegalArgumentException("Production tick decoding requires FULL market-data detail");
        var values = new HashMap<Long, LiveMarketDataInstrument>();
        for (LiveMarketDataInstrument instrument : subscription.instruments()) {
            long token;
            try {
                token = Long.parseUnsignedLong(instrument.providerInstrumentId());
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("Zerodha instrument token must be an unsigned integer", failure);
            }
            if (token > 0xffff_ffffL) throw new IllegalArgumentException("Zerodha instrument token exceeds uint32");
            values.put(token, instrument);
        }
        instrumentsByToken = Map.copyOf(values);
    }

    public List<MarketTick> decode(ByteBuffer source, Instant receivedAt) {
        Objects.requireNonNull(source, "Binary frame is required");
        Objects.requireNonNull(receivedAt, "Receipt time is required");
        var frame = source.asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN);
        if (frame.remaining() == 1) return List.of(); // Kite heartbeat.
        if (frame.remaining() < Short.BYTES) throw malformed("Frame does not contain packet count");
        int packetCount = Short.toUnsignedInt(frame.getShort());
        if (packetCount == 0) throw malformed("Frame packet count is zero");
        var ticks = new ArrayList<MarketTick>(packetCount);
        for (int index = 0; index < packetCount; index++) {
            if (frame.remaining() < Short.BYTES) throw malformed("Frame is missing packet length");
            int packetLength = Short.toUnsignedInt(frame.getShort());
            if (frame.remaining() < packetLength) throw malformed("Frame packet is truncated");
            var packet = frame.slice().order(ByteOrder.BIG_ENDIAN);
            packet.limit(packetLength); frame.position(frame.position() + packetLength);
            ticks.add(decodePacket(packet, packetLength, receivedAt));
        }
        if (frame.hasRemaining()) throw malformed("Frame contains trailing bytes");
        return List.copyOf(ticks);
    }

    private MarketTick decodePacket(ByteBuffer packet, int length, Instant receivedAt) {
        if (length < Integer.BYTES) throw malformed("Packet does not contain an instrument token");
        long token = Integer.toUnsignedLong(packet.getInt(0));
        var configured = instrumentsByToken.get(token);
        if (configured == null) throw malformed("Packet contains an unsubscribed instrument token");
        int expected = configured.kind() == MarketDataInstrumentKind.EQUITY ? EQUITY_FULL_BYTES : INDEX_FULL_BYTES;
        if (length != expected) throw malformed("Packet length does not match configured instrument kind and FULL mode");
        try {
            return configured.kind() == MarketDataInstrumentKind.EQUITY
                    ? equity(packet, configured, receivedAt) : index(packet, configured, receivedAt);
        } catch (BrokerFailure failure) {
            throw failure;
        } catch (IllegalArgumentException failure) {
            throw malformed(failure.getMessage());
        }
    }

    private static EquityMarketTick equity(ByteBuffer packet, LiveMarketDataInstrument configured, Instant receivedAt) {
        Instant exchangeAt = timestamp(packet.getInt(60), "exchange");
        Instant lastTradeAt = optionalTimestamp(packet.getInt(44));
        return new EquityMarketTick(configured.instrument(), configured.providerInstrumentId(),
                price(packet.getInt(4)), unsigned(packet.getInt(8)), price(packet.getInt(12)),
                unsigned(packet.getInt(16)), unsigned(packet.getInt(20)), unsigned(packet.getInt(24)),
                price(packet.getInt(28)), price(packet.getInt(32)), price(packet.getInt(36)), price(packet.getInt(40)),
                depth(packet, 64), depth(packet, 124),
                lastTradeAt, exchangeAt, receivedAt);
    }

    private static List<MarketDepthLevel> depth(ByteBuffer packet, int start) {
        var levels = new ArrayList<MarketDepthLevel>(5);
        for (int index = 0; index < 5; index++) {
            int offset = start + (index * 12);
            levels.add(new MarketDepthLevel(index + 1, price(packet.getInt(offset + 4)),
                    unsigned(packet.getInt(offset)), Short.toUnsignedInt(packet.getShort(offset + 8))));
        }
        return List.copyOf(levels);
    }

    private static IndexMarketTick index(ByteBuffer packet, LiveMarketDataInstrument configured, Instant receivedAt) {
        return new IndexMarketTick(configured.instrument(), configured.providerInstrumentId(), price(packet.getInt(4)),
                price(packet.getInt(16)), price(packet.getInt(8)), price(packet.getInt(12)), price(packet.getInt(20)),
                signedScaled(packet.getInt(24)), timestamp(packet.getInt(28), "exchange"), receivedAt);
    }

    private static BigDecimal price(int raw) {
        if (raw < 0) throw malformed("Price field is negative");
        return signedScaled(raw);
    }

    private static BigDecimal signedScaled(int raw) {
        return BigDecimal.valueOf(raw).divide(PRICE_DIVISOR);
    }

    private static long unsigned(int raw) {
        return Integer.toUnsignedLong(raw);
    }

    private static Instant timestamp(int raw, String field) {
        long seconds = Integer.toUnsignedLong(raw);
        if (seconds == 0) throw malformed("Packet " + field + " timestamp is absent");
        return Instant.ofEpochSecond(seconds);
    }

    private static Instant optionalTimestamp(int raw) {
        long seconds = Integer.toUnsignedLong(raw);
        return seconds == 0 ? null : Instant.ofEpochSecond(seconds);
    }

    private static BrokerFailure malformed(String message) {
        return new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE, "Malformed Zerodha ticker data: " + message);
    }
}
