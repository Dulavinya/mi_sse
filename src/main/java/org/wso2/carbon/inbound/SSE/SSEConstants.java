package org.wso2.carbon.inbound.SSE;

/**
 * Constants for SSE-Based MCP Inbound Endpoint
 */
public final class SSEConstants {

    // Endpoint configuration parameters
    public static final String ENDPOINT_NAME = "endpointName";
    public static final String ENDPOINT_TYPE = "endpointType";
    
    // MCP Protocol
    public static final String MCP_PROTOCOL_VERSION = "2024-11-05";
    public static final String MCP_SERVER_NAME = "SSE-MCP-Server";
    public static final String MCP_SERVER_VERSION = "1.0.0";
    
    // Private constructor to prevent instantiation
    private SSEConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}