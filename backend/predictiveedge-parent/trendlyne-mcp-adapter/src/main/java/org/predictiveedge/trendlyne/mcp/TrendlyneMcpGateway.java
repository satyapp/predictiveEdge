package org.predictiveedge.trendlyne.mcp;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/** Application-facing facade for Trendlyne's remote MCP tools. */
public final class TrendlyneMcpGateway implements AutoCloseable {
    private final TrendlyneMcpOperations operations;
    private final Object lifecycleMonitor = new Object();
    private volatile boolean initialized;
    private volatile boolean closed;

    TrendlyneMcpGateway(TrendlyneMcpOperations operations) {
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    public List<Tool> listTools() {
        ensureInitialized();
        return List.copyOf(operations.listTools().tools());
    }

    public CallToolResult callTool(String name, Map<String, Object> arguments) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Trendlyne MCP tool name is required");
        }
        ensureInitialized();
        return operations.callTool(name, arguments == null ? Map.of() : Map.copyOf(arguments));
    }

    private void ensureInitialized() {
        if (closed) {
            throw new IllegalStateException("Trendlyne MCP gateway is closed");
        }
        if (initialized) {
            return;
        }
        synchronized (lifecycleMonitor) {
            if (closed) {
                throw new IllegalStateException("Trendlyne MCP gateway is closed");
            }
            if (!initialized) {
                operations.initialize();
                initialized = true;
            }
        }
    }

    @Override
    public void close() {
        synchronized (lifecycleMonitor) {
            if (!closed) {
                closed = true;
                operations.close();
            }
        }
    }
}
