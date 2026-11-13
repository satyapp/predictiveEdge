package org.predictiveedge.nse.mcp;

import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;

interface NseMcpOperations extends AutoCloseable {
    void initialize();

    ListToolsResult listTools();

    CallToolResult callTool(String name, Map<String, Object> arguments);

    @Override
    void close();
}
