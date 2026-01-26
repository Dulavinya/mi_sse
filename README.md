# WSO2 MI SSE-Based MCP Server Inbound Endpoint

A Server-Sent Events (SSE) inbound endpoint for WSO2 Micro Integrator that provides Model Context Protocol (MCP) server capabilities. Allows LLM clients (Copilot, Claude, etc.) to communicate with MI integrations over HTTP.

## Features

- SSE-based streaming for real-time communication
- Full MCP protocol support
- Thread-safe connection management
- Axis2 native integration
- Event ID tracking and error handling

## Configuration

```xml
<inboundEndpoint xmlns="http://ws.apache.org/ns/synapse"
                 name="MCPSSEServerEndpoint"
                 protocol="http"
                 suspend="false">
    <parameters>
        <parameter name="inbound.http.port">8765</parameter>
        <parameter name="inbound.http.context.path">/mcp</parameter>
        <parameter name="sequence">mcpProcessingSequence</parameter>
        <parameter name="error.sequence">mcpErrorSequence</parameter>
    </parameters>
</inboundEndpoint>
```

## MCP Methods Supported

- `initialize` - Initialize connection
- `tools/list` - List available tools
- `tools/call` - Execute tool
- `resources/list` - List resources
- `resources/read` - Read resource

## Build

```bash
mvn clean install
```

## Deploy

Copy the JAR to `<MI_HOME>/dropins/` and restart MI.

## License

Apache License 2.0
