package org.wso2.carbon.inbound.SSE;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.config.SynapseConfiguration;
import org.json.JSONObject;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;

/**
 * Mediator to handle MCP requests - parses JSON, calls MCPHandler, returns response
 */
public class MCPRequestMediator extends AbstractMediator {
    
    private static final Log log = LogFactory.getLog(MCPRequestMediator.class);
    
    @Override
    public boolean mediate(MessageContext synCtx) {
        try {
            log.info("MCPRequestMediator: Processing MCP request");
            
            // Get the request body
            OMElement bodyElement = null;
            org.apache.axiom.om.OMNode firstNode = synCtx.getEnvelope().getBody().getFirstOMChild();
            if (firstNode instanceof OMElement) {
                bodyElement = (OMElement) firstNode;
            }
            String requestBody = bodyElement != null ? bodyElement.getText() : "{}";
            
            log.info("MCPRequestMediator: Request body: " + requestBody);
            
            // Parse JSON request
            JSONObject requestJson = new JSONObject(requestBody);
            String method = requestJson.getString("method");
            JSONObject params = requestJson.optJSONObject("params");
            if (params == null) {
                params = new JSONObject();
            }
            long requestId = requestJson.optLong("id", 1);
            
            log.info("MCPRequestMediator: Method=" + method + ", ID=" + requestId);
            
            // Get SynapseConfiguration and create MCPHandler
            SynapseConfiguration synConfig = synCtx.getConfiguration();
            MCPHandler handler = new MCPHandler(synConfig, "testMcpEntry");
            
            // Call MCPHandler
            JSONObject response = handler.handleCommand(method, params, requestId);
            
            log.info("MCPRequestMediator: Response: " + response.toString());
            
            // Set response in message
            OMFactory factory = synCtx.getEnvelope().getOMFactory();
            OMElement responseElement = factory.createOMElement("Response", null);
            responseElement.setText(response.toString());
            
            // Replace body
            org.apache.axiom.om.OMNode existingNode = synCtx.getEnvelope().getBody().getFirstOMChild();
            if (existingNode != null) {
                existingNode.detach();
            }
            synCtx.getEnvelope().getBody().addChild(responseElement);
            
            return true;
            
        } catch (Exception e) {
            log.error("MCPRequestMediator: Error - " + e.getMessage(), e);
            
            try {
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("jsonrpc", "2.0");
                errorResponse.put("id", 1);
                JSONObject error = new JSONObject();
                error.put("code", -32603);
                error.put("message", "Internal error: " + e.getMessage());
                errorResponse.put("error", error);
                
                OMFactory factory = synCtx.getEnvelope().getOMFactory();
                OMElement errorElement = factory.createOMElement("Error", null);
                errorElement.setText(errorResponse.toString());
                
                org.apache.axiom.om.OMNode existingNode = synCtx.getEnvelope().getBody().getFirstOMChild();
                if (existingNode != null) {
                    existingNode.detach();
                }
                synCtx.getEnvelope().getBody().addChild(errorElement);
                
            } catch (Exception ex) {
                log.error("MCPRequestMediator: Failed to create error response", ex);
            }
            
            return true;
        }
    }
}
