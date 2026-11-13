package org.predictiveedge.trendlyne.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;

class TrendlyneMcpGatewayTest {
    @Test
    void initializesOnceAndDelegatesCalls() {
        var operations = new RecordingOperations();
        var gateway = new TrendlyneMcpGateway(operations);

        gateway.listTools();
        CallToolResult result = gateway.callTool("search_entities", Map.of("query", "TCS"));

        assertThat(operations.initializeCount).isEqualTo(1);
        assertThat(operations.lastToolName).isEqualTo("search_entities");
        assertThat(result).isSameAs(operations.result);
    }

    @Test
    void rejectsBlankToolNamesBeforeConnecting() {
        var operations = new RecordingOperations();
        var gateway = new TrendlyneMcpGateway(operations);

        assertThatThrownBy(() -> gateway.callTool(" ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(operations.initializeCount).isZero();
    }

    @Test
    void cannotReconnectAfterClose() {
        var operations = new RecordingOperations();
        var gateway = new TrendlyneMcpGateway(operations);
        gateway.close();

        assertThatThrownBy(gateway::listTools).isInstanceOf(IllegalStateException.class);
        assertThat(operations.closed).isTrue();
    }

    private static final class RecordingOperations implements TrendlyneMcpOperations {
        private final CallToolResult result = CallToolResult.builder().content(java.util.List.of()).build();
        private int initializeCount;
        private String lastToolName;
        private boolean closed;

        @Override
        public void initialize() {
            initializeCount++;
        }

        @Override
        public ListToolsResult listTools() {
            return ListToolsResult.builder(java.util.List.of()).build();
        }

        @Override
        public CallToolResult callTool(String name, Map<String, Object> arguments) {
            lastToolName = name;
            return result;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
