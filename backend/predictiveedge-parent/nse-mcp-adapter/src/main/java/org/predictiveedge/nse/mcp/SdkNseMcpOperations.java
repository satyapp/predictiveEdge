package org.predictiveedge.nse.mcp;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;

final class SdkNseMcpOperations implements NseMcpOperations {
    private final McpSyncClient client;

    SdkNseMcpOperations(URI url, Duration requestTimeout) {
        NseMcpEndpoint endpoint = NseMcpEndpoint.from(url);
        var transport = HttpClientStreamableHttpTransport.builder(endpoint.baseUrl())
                .endpoint(endpoint.endpoint())
                .build();
        this.client = McpClient.sync(transport)
                .requestTimeout(requestTimeout)
                .build();
    }

    @Override
    public void initialize() {
        client.initialize();
    }

    @Override
    public ListToolsResult listTools() {
        return client.listTools();
    }

    @Override
    public CallToolResult callTool(String name, Map<String, Object> arguments) {
        return client.callTool(CallToolRequest.builder(name).arguments(arguments).build());
    }

    @Override
    public void close() {
        client.closeGracefully();
    }
}
