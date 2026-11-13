package org.predictiveedge.nse.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;

class NseMcpGatewayTest {
    @Test
    void initializesEachNseServerIndependentlyAndOnlyOnce() {
        var bhavcopy = new RecordingOperations();
        var cmMarket = new RecordingOperations();
        var gateway = gateway(bhavcopy, cmMarket);

        gateway.listTools(NseMcpServer.BHAVCOPY);
        gateway.callTool(NseMcpServer.BHAVCOPY, "get_stock_history", Map.of("symbol", "INFY"));
        gateway.listTools(NseMcpServer.CM_MARKET);

        assertThat(bhavcopy.initializeCount).isEqualTo(1);
        assertThat(cmMarket.initializeCount).isEqualTo(1);
        assertThat(bhavcopy.lastToolName).isEqualTo("get_stock_history");
    }

    @Test
    void rejectsBlankToolNamesBeforeConnecting() {
        var bhavcopy = new RecordingOperations();
        var gateway = gateway(bhavcopy, new RecordingOperations());

        assertThatThrownBy(() -> gateway.callTool(NseMcpServer.BHAVCOPY, " ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(bhavcopy.initializeCount).isZero();
    }

    @Test
    void closesBothConnections() {
        var bhavcopy = new RecordingOperations();
        var cmMarket = new RecordingOperations();
        var gateway = gateway(bhavcopy, cmMarket);

        gateway.close();

        assertThat(bhavcopy.closed).isTrue();
        assertThat(cmMarket.closed).isTrue();
        assertThatThrownBy(() -> gateway.listTools(NseMcpServer.CM_MARKET))
                .isInstanceOf(IllegalStateException.class);
    }

    private static NseMcpGateway gateway(RecordingOperations bhavcopy, RecordingOperations cmMarket) {
        var operations = new EnumMap<NseMcpServer, NseMcpOperations>(NseMcpServer.class);
        operations.put(NseMcpServer.BHAVCOPY, bhavcopy);
        operations.put(NseMcpServer.CM_MARKET, cmMarket);
        return new NseMcpGateway(operations);
    }

    private static final class RecordingOperations implements NseMcpOperations {
        private int initializeCount;
        private String lastToolName;
        private boolean closed;

        @Override
        public void initialize() {
            initializeCount++;
        }

        @Override
        public ListToolsResult listTools() {
            return ListToolsResult.builder(List.of()).build();
        }

        @Override
        public CallToolResult callTool(String name, Map<String, Object> arguments) {
            lastToolName = name;
            return CallToolResult.builder().content(List.of()).build();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
