package org.predictiveedge.nse.mcp;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/** Application-facing facade for NSE's Bhavcopy and cash-market MCP tools. */
public final class NseMcpGateway implements AutoCloseable {
    private final Map<NseMcpServer, ManagedConnection> connections;

    NseMcpGateway(Map<NseMcpServer, NseMcpOperations> operations) {
        Objects.requireNonNull(operations, "operations");
        var managed = new EnumMap<NseMcpServer, ManagedConnection>(NseMcpServer.class);
        for (NseMcpServer server : NseMcpServer.values()) {
            managed.put(server, new ManagedConnection(Objects.requireNonNull(operations.get(server), server.name())));
        }
        this.connections = Map.copyOf(managed);
    }

    public List<Tool> listTools(NseMcpServer server) {
        ManagedConnection connection = connection(server);
        connection.ensureInitialized();
        return List.copyOf(connection.operations.listTools().tools());
    }

    public CallToolResult callTool(NseMcpServer server, String name, Map<String, Object> arguments) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("NSE MCP tool name is required");
        }
        ManagedConnection connection = connection(server);
        connection.ensureInitialized();
        return connection.operations.callTool(name, arguments == null ? Map.of() : Map.copyOf(arguments));
    }

    private ManagedConnection connection(NseMcpServer server) {
        return connections.get(Objects.requireNonNull(server, "NSE MCP server is required"));
    }

    @Override
    public void close() {
        connections.values().forEach(ManagedConnection::close);
    }

    private static final class ManagedConnection {
        private final NseMcpOperations operations;
        private boolean initialized;
        private boolean closed;

        private ManagedConnection(NseMcpOperations operations) {
            this.operations = operations;
        }

        private synchronized void ensureInitialized() {
            if (closed) {
                throw new IllegalStateException("NSE MCP connection is closed");
            }
            if (!initialized) {
                operations.initialize();
                initialized = true;
            }
        }

        private synchronized void close() {
            if (!closed) {
                closed = true;
                operations.close();
            }
        }
    }
}
