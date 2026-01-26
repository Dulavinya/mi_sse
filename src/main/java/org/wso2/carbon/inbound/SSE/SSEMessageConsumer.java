package org.wso2.carbon.inbound.SSE;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.wso2.carbon.inbound.endpoint.protocol.generic.GenericInboundListener;
import org.json.JSONObject;

import org.apache.axis2.context.MessageContext;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


//SSE-Based Inbound Endpoint 

public class SSEMessageConsumer extends GenericInboundListener {

    private static final Log log = LogFactory.getLog(SSEMessageConsumer.class);
    
    private final AtomicBoolean listening = new AtomicBoolean(false);
    private final AtomicBoolean clientConnected = new AtomicBoolean(false);
    private MCPHandler mcpHandler;
    
    
    private MessageContext clientMessageContext;
    private final AtomicLong eventIdCounter = new AtomicLong(0);

    
    public SSEMessageConsumer(InboundProcessorParams params) {
        super(params);
        this.mcpHandler = new MCPHandler();
        log.info("SSE Inbound Endpoint with MCP capability created: " + name);
    }

    
     // init
     
    @Override
    public void init() {
        log.info("Initializing SSE inbound endpoint with MCP: " + name);
        try {
            // Initialize 
            if (mcpHandler == null) {
                mcpHandler = new MCPHandler();
            }
            
            listening.set(true);
            log.info("SSE endpoint initialized and listening for MCP commands");
        } catch (Exception e) {
            log.error("Failed to initialize SSE endpoint: " + name, e);
            listening.set(false);
        }
    }

   //destroy
    @Override
    public void destroy() {
        log.info("Destroying SSE inbound endpoint with MCP: " + name);
        try {
            listening.set(false);
            disconnectClient();
            mcpHandler = null;
            log.info("SSE endpoint destroyed");
        } catch (Exception e) {
            log.error("Error destroying SSE endpoint: " + name, e);
        }
    }

   //pausing the endpoint
    public void pause() {
        log.info("Pausing SSE inbound endpoint: " + name);
        listening.set(false);
    }

    //activate
    public boolean activate() {
        log.info("Activating SSE inbound endpoint: " + name);
        try {
            if (!listening.get()) {
                listening.set(true);
                log.info("SSE endpoint activated");
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to activate SSE endpoint: " + name, e);
            return false;
        }
    }

    //deactivate
    public boolean deactivate() {
        log.info("Deactivating SSE inbound endpoint with MCP: " + name);
        try {
            listening.set(false);
            disconnectClient();
            return true;
        } catch (Exception e) {
            log.error("Error deactivating SSE endpoint: " + name, e);
            return false;
        }
    }

    //isDeactivated
    public boolean isDeactivated() {
        return !listening.get();
    }

    /**
     * Handle MCP command from client via persistent SSE connection
     * Response is sent via SSE event, connection stays open
     */
    public JSONObject handleMCPCommand(String method, JSONObject params) {
        JSONObject response = new JSONObject();
        
        if (!listening.get()) {
            log.warn("MCP command received but endpoint is not listening: " + name);
            response.put("error", new JSONObject()
                .put("code", -32603)
                .put("message", "Endpoint not listening"));
            sendSSEEventToClient(response);
            return response;
        }
        
        try {
            log.debug("Handling MCP command: " + method + ", params: " + params.toString());
            
            // Route to MCPHandler for MCP protocol handling
            response = mcpHandler.handleCommand(method, params);
            
            // Send response via SSE (connection stays open - no sequence respond() call)
            sendSSEEventToClient(response);
            
            log.debug("MCP response sent via SSE to client: " + response.toString());
            
        } catch (Exception e) {
            log.error("Error handling MCP command: " + method, e);
            response.put("error", new JSONObject()
                .put("code", -32603)
                .put("message", "Internal error: " + e.getMessage()));
            
            sendSSEEventToClient(response);
        }
        
        return response;
    }

   // GET MCP HANDLER
    public MCPHandler getMCPHandler() {
        return mcpHandler;
    }

    //islistening()
    public boolean isListening() {
        return listening.get();
    }

    /**
     * Called when client first connects (HTTP POST with SSE headers)
     * Stores MessageContext for persistent connection; connection stays open for multiple commands
     */
    public void connectClient(MessageContext msgContext) {
        try {
            this.clientMessageContext = msgContext;
            clientConnected.set(true);
            
            // Set SSE response headers on MessageContext
            msgContext.setProperty("Content-Type", "text/event-stream");
            msgContext.setProperty("Cache-Control", "no-cache");
            msgContext.setProperty("Connection", "keep-alive");
            
            log.info("SSE client connected - persistent connection established for: " + name);
            
            // Send welcome event
            sendSSEEventToClient(new JSONObject()
                .put("type", "welcome")
                .put("message", "Connected to WSO2 MI SSE-based MCP endpoint")
                .put("endpoint", name)
                .put("timestamp", System.currentTimeMillis()));
            
        } catch (Exception e) {
            log.error("Error connecting SSE client: " + name, e);
            clientConnected.set(false);
        }
    }

    /**
     * Called when client disconnects or connection closes
     * Cleans up persistent connection resources
     */
    public void disconnectClient() {
        try {
            if (clientMessageContext != null) {
                clientMessageContext = null;
            }
            clientConnected.set(false);
            log.info("SSE client disconnected from: " + name);
        } catch (Exception e) {
            log.error("Error disconnecting client from: " + name, e);
        }
    }

    /**
     * Check if SSE client is currently connected via persistent connection
     */
    public boolean isClientConnected() {
        return clientConnected.get() && clientMessageContext != null;
    }

    /**
     * Send SSE formatted event to client via persistent connection
     * Connection stays open after sending (no sequence respond() called)
     * Multiple events can be sent through same MessageContext
     */
    public void sendSSEEventToClient(JSONObject eventData) {
        if (!clientConnected.get() || clientMessageContext == null) {
            log.warn("Cannot send SSE event: client not connected to: " + name);
            return;
        }
        
        try {
            long eventId = eventIdCounter.incrementAndGet();
            
            // Format event in SSE protocol: id, event type, data
            StringBuilder sseEvent = new StringBuilder();
            sseEvent.append("id: ").append(eventId).append("\n");
            sseEvent.append("event: mcp_response\n");
            sseEvent.append("data: ").append(eventData.toString()).append("\n\n");
            
            // Add to SOAP envelope body as OMElement
            OMFactory omFactory = clientMessageContext.getEnvelope().getOMFactory();
            OMElement responseElement = omFactory.createOMElement("sseEvent", null);
            responseElement.setText(sseEvent.toString());
            
            clientMessageContext.getEnvelope().getBody().addChild(responseElement);
            
            log.debug("SSE event sent to client on: " + name + " - Event ID: " + eventId + 
                      ", Data: " + eventData.toString());
            
            // ✅ CRITICAL: No respond() called on sequence - connection stays open for next command
            
        } catch (Exception e) {
            log.error("Error sending SSE event to client on: " + name, e);
            disconnectClient();
        }
    }

    
    
}
