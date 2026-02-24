package org.wso2.carbon.inbound.SSE;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.SynapseEnvironment;
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
    private static final String MCP_TOOLS_LOCALENTRY_PARAM = "mcp.tools.localentry";
    
    private final AtomicBoolean listening = new AtomicBoolean(false);
    private final AtomicBoolean clientConnected = new AtomicBoolean(false);
    private MCPHandler mcpHandler;
    private SynapseConfiguration synapseConfig;
    private String toolsLocalEntryKey;
    
    
    private MessageContext clientMessageContext;
    private final AtomicLong eventIdCounter = new AtomicLong(0);

    
    public SSEMessageConsumer(InboundProcessorParams params) {
        super(params);
        // Get SynapseConfiguration from InboundProcessorParams
        if (params.getSynapseEnvironment() != null) {
            this.synapseConfig = params.getSynapseEnvironment().getSynapseConfiguration();
        }
        
        // Get the LocalEntry reference from inbound endpoint parameters
        this.toolsLocalEntryKey = params.getProperties().getProperty(MCP_TOOLS_LOCALENTRY_PARAM);
        if (toolsLocalEntryKey == null) {
            toolsLocalEntryKey = "mcp_tools"; // Default value
        }
        
        this.mcpHandler = new MCPHandler(synapseConfig, toolsLocalEntryKey);
        log.info("SSE Inbound Endpoint with MCP capability created: " + name + 
                 ", Tools LocalEntry: " + toolsLocalEntryKey);
    }

    
     // init
     
    @Override
    public void init() {
        log.info("Initializing SSE inbound endpoint with MCP: " + name + 
                 ", Using tools from LocalEntry: " + toolsLocalEntryKey);
        try {
            // Initialize MCP Handler with SynapseConfiguration and LocalEntry key
            if (mcpHandler == null) {
                mcpHandler = new MCPHandler(synapseConfig, toolsLocalEntryKey);
            } else if (synapseConfig != null) {
                mcpHandler.setSynapseConfiguration(synapseConfig);
                mcpHandler.setToolsLocalEntryKey(toolsLocalEntryKey);
            }
            
            listening.set(true);
            log.info("SSE endpoint initialized and listening for MCP commands from LocalEntry: " + toolsLocalEntryKey);
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
            
            
            response = mcpHandler.handleCommand(method, params);
            
        
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

    
    public void connectClient(MessageContext msgContext) {
        try {
            this.clientMessageContext = msgContext;
            clientConnected.set(true);
            
          
            msgContext.setProperty("Content-Type", "text/event-stream");
            msgContext.setProperty("Cache-Control", "no-cache");
            msgContext.setProperty("Connection", "keep-alive");
            
            
            sendSSEEventToClient(new JSONObject()
                
                .put("message", "Connected ")
                .put("endpoint", name));
                
            
        } catch (Exception e) {
            log.error("Error connecting : " + name, e);
            clientConnected.set(false);
        }
    }

    
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

    
    public boolean isClientConnected() {
        return clientConnected.get() && clientMessageContext != null;
    }

    
    public void sendSSEEventToClient(JSONObject eventData) {
        if (!clientConnected.get() || clientMessageContext == null) {
            log.warn("Cannot send SSE event: client not connected to: " + name);
            return;
        }
        
        try {
            long eventId = eventIdCounter.incrementAndGet();
            
           
            StringBuilder sseEvent = new StringBuilder();
            sseEvent.append("id: ").append(eventId).append("\n");
            sseEvent.append("event: mcp_response\n");
            sseEvent.append("data: ").append(eventData.toString()).append("\n\n");
            
            
            OMFactory omFactory = clientMessageContext.getEnvelope().getOMFactory();
            OMElement responseElement = omFactory.createOMElement("sseEvent", null);
            responseElement.setText(sseEvent.toString());
            
            clientMessageContext.getEnvelope().getBody().addChild(responseElement);
            
            log.debug("SSE event sent to client on: " + name + " - Event ID: " + eventId + 
                      ", Data: " + eventData.toString());
            
            
            
        } catch (Exception e) {
            log.error("Error", e);
            disconnectClient();
        }
    }

    
    
}
