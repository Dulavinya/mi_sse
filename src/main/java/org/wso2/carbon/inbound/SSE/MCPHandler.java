package org.wso2.carbon.inbound.SSE;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.json.JSONArray;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.registry.Registry;
import java.util.Map;

public class MCPHandler {
    
    private static final Log log = LogFactory.getLog(MCPHandler.class);
    private SynapseConfiguration synapseConfig;
    private String toolsLocalEntryKey;
    
    public MCPHandler() {
    }
    
    public MCPHandler(SynapseConfiguration synapseConfig) {
        this.synapseConfig = synapseConfig;
        this.toolsLocalEntryKey = "mcp_tools"; // Default
    }
    
    public MCPHandler(SynapseConfiguration synapseConfig, String toolsLocalEntryKey) {
        this.synapseConfig = synapseConfig;
        this.toolsLocalEntryKey = toolsLocalEntryKey;
    }
    
    public void setSynapseConfiguration(SynapseConfiguration synapseConfig) {
        this.synapseConfig = synapseConfig;
    }
    
    public void setToolsLocalEntryKey(String toolsLocalEntryKey) {
        this.toolsLocalEntryKey = toolsLocalEntryKey;
    }
    
    public JSONObject handleCommand(String method, JSONObject params) {
        return handleCommand(method, params, System.currentTimeMillis());
    }

    public JSONObject handleCommand(String method, JSONObject params, long requestId) {
        JSONObject response = new JSONObject();
        
        try {
            JSONObject result = null;
            
            switch (method) {
                case "initialize":
                    result = handleInitialize(params, requestId);
                    break;
                case "tools/list":
                    result = handleToolsList(params, requestId);
                    break;
                case "tools/call":
                    result = handleToolCall(params, requestId);
                    break;
                default:
                    return createErrorResponse(requestId, -32601, "Method not found: " + method);
            }
            
            response.put("jsonrpc", "2.0");
            response.put("id", requestId);
            response.put("result", result);
            
        } catch (Exception e) {
            log.error("Error handling MCP command: " + method, e);
            return createErrorResponse(requestId, -32603, "Internal error: " + e.getMessage());
        }
        
        return response;
    }
    
    //initialize
    private JSONObject handleInitialize(JSONObject params, long requestId) {
        JSONObject result = new JSONObject();
        
        JSONObject serverInfo = new JSONObject();
        serverInfo.put("name", "SSE-MCP-Server");
        serverInfo.put("version", "1.0.0");
        result.put("serverInfo", serverInfo);
        
        result.put("protocolVersion", "2024-11-05");
        
        JSONObject capabilities = new JSONObject();
        capabilities.put("tools", new JSONObject());
        result.put("capabilities", capabilities);
        
        log.info("[" + requestId + "] Initialize request handled, returning server info");
        return result;
    }
    
    //tool list 
    private JSONObject handleToolsList(JSONObject params, long requestId) {
        JSONArray tools = new JSONArray();
        
        try {
            if (synapseConfig == null) {
                log.warn("[" + requestId + "] SynapseConfiguration not available, returning empty tool list");
                JSONObject result = new JSONObject();
                result.put("tools", tools);
                return result;
            }

            Map<String, Map<String, Object>> mcpToolsMap = getMcpToolsMap();
            
            if (mcpToolsMap != null && !mcpToolsMap.isEmpty()) {
                log.info("[" + requestId + "] Found " + mcpToolsMap.size() + " total MCP tool configurations in map");
                
                String filterPrefix = toolsLocalEntryKey + ":";
                int filteredCount = 0;

                for (Map.Entry<String, Map<String, Object>> toolEntry : mcpToolsMap.entrySet()) {
                    String compositeKey = toolEntry.getKey();

                    if (!compositeKey.startsWith(filterPrefix)) {
                        continue;
                    }
                    
                    filteredCount++;
                    Map<String, Object> toolConfig = toolEntry.getValue();
                    
                    JSONObject tool = new JSONObject();
                    
                    // Extract tool name and details
                    if (toolConfig.containsKey("name")) {
                        tool.put("name", toolConfig.get("name"));
                    } else {
                        String[] parts = compositeKey.split(":");
                        tool.put("name", parts.length > 1 ? parts[1] : compositeKey);
                    }
                    
                    if (toolConfig.containsKey("description")) {
                        tool.put("description", toolConfig.get("description"));
                    }
                    
                    if (toolConfig.containsKey("inputSchema")) {
                        tool.put("inputSchema", toolConfig.get("inputSchema"));
                    }
                    
                    tools.put(tool);
                    log.debug("[" + requestId + "] Added tool from LocalEntry " + toolsLocalEntryKey + ": " + tool.getString("name"));
                }
                
                log.info("[" + requestId + "] Retrieved " + filteredCount + " tools from LocalEntry: " + toolsLocalEntryKey);
            } else {
                log.info("[" + requestId + "] No MCP tools configured in SynapseConfiguration");
            }
            
        } catch (Exception e) {
            log.error("[" + requestId + "] Error retrieving tools list from SynapseConfiguration", e);
            throw new RuntimeException("Failed to retrieve tools: " + e.getMessage(), e);
        }
        
        JSONObject result = new JSONObject();
        result.put("tools", tools);
        return result;
    }
    
    
    private JSONObject handleToolCall(JSONObject params, long requestId) {
        try {
            if (synapseConfig == null) {
                throw new RuntimeException("SynapseConfiguration not available");
            }
            
            if (!params.has("name")) {
                throw new IllegalArgumentException("Missing required parameter: name");
            }
            
            String toolName = params.getString("name");
            JSONObject arguments = params.has("arguments") ? params.getJSONObject("arguments") : new JSONObject();
            
            log.info("[" + requestId + "] Executing tool: " + toolName + " from LocalEntry: " + toolsLocalEntryKey);
            
            Map<String, Map<String, Object>> mcpToolsMap = getMcpToolsMap();
            Map<String, Object> toolConfig = null;
            String foundCompositeKey = null;
            
            String filterPrefix = toolsLocalEntryKey + ":";
            
            if (mcpToolsMap != null) {
                for (Map.Entry<String, Map<String, Object>> entry : mcpToolsMap.entrySet()) {
                    String compositeKey = entry.getKey();
                    
                    if (!compositeKey.startsWith(filterPrefix)) {
                        continue;
                    }
                    
                    Map<String, Object> config = entry.getValue();
                    String configuredName = (String) config.get("name");
                    
                    if (toolName.equals(configuredName)) {
                        toolConfig = config;
                        foundCompositeKey = entry.getKey();
                        break;
                    }
                }
            }
            
            if (toolConfig == null) {
                throw new IllegalArgumentException("Tool not found in LocalEntry " + toolsLocalEntryKey + ": " + toolName);
            }
            //  invoke the associated sequence
            
            JSONObject result = new JSONObject();
            result.put("toolName", toolName);
            result.put("compositeKey", foundCompositeKey);
            result.put("localEntryKey", toolsLocalEntryKey);
            result.put("status", "ready_for_execution");
            result.put("arguments", arguments);
            
            // Include tool configuration details
            if (toolConfig.containsKey("sequenceName")) {
                result.put("sequenceName", toolConfig.get("sequenceName"));
            }
            
            log.info("[" + requestId + "] Tool " + toolName + " from LocalEntry " + toolsLocalEntryKey + " is ready for execution");
            return result;
            
        } catch (IllegalArgumentException e) {
            log.warn("[" + requestId + "] Invalid tool call request: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[" + requestId + "] Error executing tool call", e);
            throw new RuntimeException("Tool execution failed: " + e.getMessage(), e);
        }
    }
    
    
    private Map<String, Map<String, Object>> getMcpToolsMap() {
        if (synapseConfig == null) {
            return null;
        }
        try {
            return synapseConfig.getMcpToolsMap();
        } catch (Exception e) {
            log.warn("Could not access mcpToolsMap from SynapseConfiguration", e);
            return null;
        }
    }
    
    
    private JSONObject createErrorResponse(long requestId, int code, String message) {
        JSONObject error = new JSONObject();
        error.put("code", code);
        error.put("message", message);
        
        JSONObject response = new JSONObject();
        response.put("jsonrpc", "2.0");
        response.put("id", requestId);
        response.put("error", error);
        
        return response;
    }
}
