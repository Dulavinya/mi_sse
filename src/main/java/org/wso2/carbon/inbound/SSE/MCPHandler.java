package org.wso2.carbon.inbound.SSE;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * MCP Protocol Handler
 * 
 * Handles JSON-RPC MCP commands:
 * - initialize: Server capabilities and protocol version
 * - tools/list: List available tools
 * - tools/call: Execute a tool
 * 
 * Separates MCP protocol logic from HTTP/SSE transport
 */
public class MCPHandler {
    
    private static final Log log = LogFactory.getLog(MCPHandler.class);
    
    /**
     * Route MCP command to appropriate handler
     * 
     * @param method MCP method name (e.g., "initialize", "tools/list")
     * @param params Method parameters
     * @return Response object with "result" or "error"
     */
    public JSONObject handleCommand(String method, JSONObject params) {
        JSONObject response = new JSONObject();
        
        try {
            switch (method) {
                case "initialize":
                    response = handleInitialize(params);
                    break;
                case "tools/list":
                    response = handleToolsList(params);
                    break;
                case "tools/call":
                    response = handleToolCall(params);
                    break;
                default:
                    response.put("error", new JSONObject()
                        .put("code", -32601)
                        .put("message", "Method not found: " + method));
            }
        } catch (Exception e) {
            log.error("Error handling MCP command: " + method, e);
            response.put("error", new JSONObject()
                .put("code", -32603)
                .put("message", "Internal error: " + e.getMessage()));
        }
        
        return response;
    }
    
    /**
     * MCP initialize command
     * Returns server capabilities and protocol version
     */
    private JSONObject handleInitialize(JSONObject params) {
        JSONObject response = new JSONObject();
        JSONObject result = new JSONObject();
        
        JSONObject serverInfo = new JSONObject();
        serverInfo.put("name", "SSE-MCP-Server");
        serverInfo.put("version", "1.0.0");
        result.put("serverInfo", serverInfo);
        
        result.put("protocolVersion", "2024-11-05");
        
        JSONObject capabilities = new JSONObject();
        capabilities.put("tools", new JSONObject());
        result.put("capabilities", capabilities);
        
        response.put("result", result);
        return response;
    }
    
    /**
     * MCP tools/list command
     * Returns list of available tools
     */
    private JSONObject handleToolsList(JSONObject params) {
        JSONObject response = new JSONObject();
        JSONArray tools = new JSONArray();
        
        // TODO: Implement tool registration
        // tools.put(new JSONObject()
        //     .put("name", "tool1")
        //     .put("description", "Tool description")
        //     .put("inputSchema", ...));
        
        JSONObject result = new JSONObject();
        result.put("tools", tools);
        response.put("result", result);
        return response;
    }
    
    /**
     * MCP tools/call command
     * Executes a tool and returns result
     */
    private JSONObject handleToolCall(JSONObject params) {
        JSONObject response = new JSONObject();
        
        // TODO: Implement tool execution
        response.put("error", new JSONObject()
            .put("code", -32601)
            .put("message", "No tools available"));
        
        return response;
    }
}
