package org.wso2.carbon.inbound.SSE;

/**
 * Constants for SSE Inbound Endpoint configuration
 */
public final class SSEConstants {

    // HTTP Endpoint Parameters
    public static final String INBOUND_ENDPOINT_PARAMETER_HTTP_PORT = "inbound.http.port";
    public static final String INBOUND_ENDPOINT_PARAMETER_CONTEXT_PATH = "inbound.http.context.path";
    public static final String ENABLE_PORT_OFFSET_FOR_INBOUND_ENDPOINT = "synapse.inbound.endpoint.enable_port_offset";
    
    // Default values
    public static final String DEFAULT_CONTEXT_PATH = "/mcp";
    public static final String DEFAULT_MCP_TOOLS_ENTRY = "mcp_tools";
    
    // MCP Configuration
    public static final String MCP_TOOLS_LOCALENTRY_PARAM = "mcp.tools.localentry";
    
    // MCP Protocol
    public static final String MCP_PROTOCOL_VERSION = "2024-11-05";
    public static final String MCP_SERVER_NAME = "SSE-MCP-Server";
    public static final String MCP_SERVER_VERSION = "1.0.0";
    
    // Endpoint Configuration
    public static final String ENDPOINT_NAME = "endpointName";
    public static final String ENDPOINT_TYPE = "endpointType";
    
    // SSE Protocol
    public static final String SSE_CONTENT_TYPE = "text/event-stream";
    public static final String SSE_EVENT_TYPE = "mcp_response";
    
    private SSEConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}