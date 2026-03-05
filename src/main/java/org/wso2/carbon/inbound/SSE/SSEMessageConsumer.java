package org.wso2.carbon.inbound.SSE;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.SynapseEnvironment;
import org.wso2.carbon.inbound.endpoint.protocol.generic.GenericInboundListener;
import org.json.JSONObject;
import org.json.JSONException;

import org.apache.axis2.context.MessageContext;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Properties;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;


//SSE-Based Inbound Endpoint 

public class SSEMessageConsumer extends GenericInboundListener {

    private static final Log log = LogFactory.getLog(SSEMessageConsumer.class);
    private static final String MCP_TOOLS_LOCALENTRY_PARAM = "mcp.tools.localentry";
    
    private final AtomicBoolean listening = new AtomicBoolean(false);
    private final AtomicBoolean clientConnected = new AtomicBoolean(false);
    private MCPHandler mcpHandler;
    private SynapseConfiguration synapseConfig;
    private String toolsLocalEntryKey;
    private HttpServer httpServer;
    private static final int DEFAULT_PORT = 8765;
    
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

    /**
     * Required constructor for GenericEventBasedListener
     * Called by WSO2 MI framework for event-based inbound endpoints
     */
    public SSEMessageConsumer(java.util.Properties properties,
                             String endpointName,
                             org.apache.synapse.core.SynapseEnvironment synapseEnvironment,
                             String inboundEndpointClass,
                             String endpointDefinition,
                             boolean sequential,
                             boolean isEncrypted) {
        // Initialize with properties - don't call super with these params
        super(new InboundProcessorParams() {
            @Override
            public String getName() { return endpointName; }
            @Override
            public SynapseEnvironment getSynapseEnvironment() { return synapseEnvironment; }
            @Override
            public java.util.Properties getProperties() { return properties; }
            @Override
            public String getProtocol() { return null; }
            @Override
            public String getClassImpl() { return inboundEndpointClass; }
        });
        
        // Get SynapseConfiguration
        if (synapseEnvironment != null) {
            this.synapseConfig = synapseEnvironment.getSynapseConfiguration();
        }
        
        // Get the LocalEntry reference from properties
        this.toolsLocalEntryKey = properties.getProperty(MCP_TOOLS_LOCALENTRY_PARAM);
        if (toolsLocalEntryKey == null) {
            toolsLocalEntryKey = "mcp_tools"; // Default value
        }
        
        this.mcpHandler = new MCPHandler(synapseConfig, toolsLocalEntryKey);
        log.info("SSE Inbound Endpoint with MCP capability created (GenericEventBased): " + endpointName + 
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
            
            log.info("Starting HTTP server initialization...");
            // Start HTTP server
            startHttpServer();
            
            listening.set(true);
            log.info("SSE endpoint initialized and listening for MCP commands on port " + DEFAULT_PORT);
        } catch (Exception e) {
            log.error("Failed to initialize SSE endpoint: " + name + ". Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
            listening.set(false);
            throw new RuntimeException("SSE endpoint initialization failed", e);
        }
    }
    
    /**
     * Start embedded HTTP server for SSE
     */
    private void startHttpServer() throws Exception {
        log.info("Starting HTTP server on port " + DEFAULT_PORT);
        try {
            log.debug("Creating HttpServer...");
            httpServer = HttpServer.create(new java.net.InetSocketAddress("0.0.0.0", DEFAULT_PORT), 50);
            log.debug("HttpServer created successfully");
            
            // Create context for /sse path
            log.debug("Registering /sse context handler");
            httpServer.createContext("/sse", exchange -> {
                try {
                    String method = exchange.getRequestMethod();
                    log.debug("Handling " + method + " request");
                    
                    if ("GET".equalsIgnoreCase(method)) {
                        handleSSEConnection(exchange);
                    } else if ("POST".equalsIgnoreCase(method)) {
                        handleMCPRequest(exchange);
                    } else {
                        exchange.sendResponseHeaders(405, -1);
                    }
                } catch (Exception e) {
                    log.error("Error handling HTTP request", e);
                    try {
                        exchange.sendResponseHeaders(500, -1);
                    } catch (Exception ex) {
                        log.debug("Error sending error response", ex);
                    }
                }
            });
            log.debug("Context handler registered");
            
            log.debug("Setting up thread pool executor");
            httpServer.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
            
            log.debug("Starting HTTP server...");
            httpServer.start();
            log.info("HTTP Server started successfully on port " + DEFAULT_PORT);
        } catch (java.net.BindException e) {
            log.error("Port " + DEFAULT_PORT + " already in use. Is another instance running?", e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to start HTTP server: " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Handle SSE GET connection
     */
    private void handleSSEConnection(HttpExchange exchange) throws Exception {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, 0);
        
        OutputStream os = exchange.getResponseBody();
        
        // Send welcome event
        String welcomeEvent = "id: 1\nevent: mcp_response\ndata: {\"message\":\"Connected to SSE MCP Endpoint\",\"endpoint\":\"" + name + "\"}\n\n";
        os.write(welcomeEvent.getBytes(StandardCharsets.UTF_8));
        os.flush();
        
        clientConnected.set(true);
        log.info("SSE client connected");
    }
    
    /**
     * Handle MCP POST request
     */
    private void handleMCPRequest(HttpExchange exchange) throws Exception {
        InputStream is = exchange.getRequestBody();
        String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        
        try {
            JSONObject requestJson = new JSONObject(requestBody);
            String commandMethod = requestJson.getString("method");
            JSONObject params = requestJson.optJSONObject("params");
            if (params == null) {
                params = new JSONObject();
            }
            long requestId = requestJson.optLong("id", 1);
            
            log.info("Processing MCP command: " + commandMethod);
            
            // Execute command
            JSONObject response = mcpHandler.handleCommand(commandMethod, params);
            response.put("id", requestId);
            response.put("jsonrpc", "2.0");
            
            // Send response
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            String responseStr = response.toString(2);
            exchange.sendResponseHeaders(200, responseStr.getBytes().length);
            exchange.getResponseBody().write(responseStr.getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().close();
            
            log.info("MCP response sent");
        } catch (JSONException e) {
            log.error("Invalid JSON in request", e);
            JSONObject error = new JSONObject();
            error.put("error", new JSONObject().put("code", -32700).put("message", "Parse error"));
            String errorStr = error.toString(2);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, errorStr.getBytes().length);
            exchange.getResponseBody().write(errorStr.getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().close();
        }
    }

   //destroy
    @Override
    public void destroy() {
        log.info("Destroying SSE inbound endpoint with MCP: " + name);
        try {
            listening.set(false);
            if (httpServer != null) {
                httpServer.stop(0);
                log.info("HTTP Server stopped");
            }
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

    /**
     * Called by WSO2 MI framework to handle incoming messages
     * Routes GET requests to SSE connection, POST to MCP command handling
     */
    public void onInject(MessageContext msgContext) {
        log.info("onInject: Processing inbound message");
        onMessage(msgContext);
    }

    public void onMessage(MessageContext msgContext) {
        log.debug("onMessage: Received HTTP request on SSE endpoint: " + name);
        
        try {
            // Check if this is an SSE connection request (GET request to establish connection)
            String httpMethod = (String) msgContext.getProperty("HTTP_METHOD");
            if ("GET".equalsIgnoreCase(httpMethod)) {
                log.debug("onMessage: Handling SSE GET request (connection establishment)");
                connectClient(msgContext);
                return;
            }
            
            // Extract JSON-RPC request body from message context
            JSONObject requestJson = extractMCPRequestFromMessage(msgContext);
            if (requestJson == null) {
                log.error("onMessage: Failed to extract MCP request from message");
                sendErrorResponse(msgContext, -32700, "Parse error - Invalid JSON-RPC request");
                return;
            }
            
            // Validate JSON-RPC structure
            if (!requestJson.has("method")) {
                log.error("onMessage: Missing 'method' in JSON-RPC request");
                sendErrorResponse(msgContext, -32600, "Invalid Request - missing method");
                return;
            }
            
            // Extract method and parameters
            String method = requestJson.getString("method");
            JSONObject params = requestJson.optJSONObject("params");
            if (params == null) {
                params = new JSONObject();
            }
            long requestId = requestJson.optLong("id", System.currentTimeMillis());
            
            log.info("onMessage: Processing MCP command - Method: " + method + 
                    ", Request ID: " + requestId);
            
            // Handle MCP command
            JSONObject response = handleMCPCommand(method, params);
            if (response == null) {
                response = new JSONObject();
            }
            
            // Add JSON-RPC response metadata
            response.put("id", requestId);
            response.put("jsonrpc", "2.0");
            
            log.debug("onMessage: MCP command completed - Response: " + response.toString(2));
            
        } catch (JSONException e) {
            log.error("onMessage: JSON parsing error", e);
            sendErrorResponse(msgContext, -32700, "Parse error - Invalid JSON");
        } catch (Exception e) {
            log.error("onMessage: Unexpected error processing MCP request", e);
            sendErrorResponse(msgContext, -32603, "Internal error: " + e.getMessage());
        }
    }
    
    /**
     * Extracts MCP JSON-RPC request from the message context
     */
    private JSONObject extractMCPRequestFromMessage(MessageContext msgContext) {
        try {
            // Get the request body from the message envelope
            org.apache.axiom.om.OMNode firstNode = msgContext.getEnvelope().getBody().getFirstOMChild();
            String requestBody = null;
            
            if (firstNode instanceof OMElement) {
                OMElement bodyElement = (OMElement) firstNode;
                requestBody = bodyElement.getText();
            } else if (firstNode != null) {
                // Try to get text content from other node types
                requestBody = firstNode.toString();
            } else {
                // Try to get raw request body if no body content
                InputStream inputStream = (InputStream) msgContext.getProperty("TRANSPORT_IN");
                if (inputStream != null) {
                    Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name());
                    requestBody = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "{}";
                    scanner.close();
                }
            }
            
            if (requestBody == null || requestBody.isEmpty()) {
                log.warn("extractMCPRequestFromMessage: Empty request body");
                return new JSONObject();
            }
            
            log.debug("extractMCPRequestFromMessage: Request body - " + requestBody);
            return new JSONObject(requestBody);
            
        } catch (Exception e) {
            log.error("extractMCPRequestFromMessage: Error extracting request", e);
            return null;
        }
    }
    
    /**
     * Sends JSON-RPC error response
     */
    private void sendErrorResponse(MessageContext msgContext, int errorCode, String errorMessage) {
        try {
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", new JSONObject()
                .put("code", errorCode)
                .put("message", errorMessage));
            errorResponse.put("jsonrpc", "2.0");
            
            // Send via SSE if client is connected
            if (isClientConnected()) {
                sendSSEEventToClient(errorResponse);
            } else {
                log.warn("sendErrorResponse: Client not connected, error not sent");
            }
        } catch (Exception e) {
            log.error("sendErrorResponse: Error sending error response", e);
        }
    }

    
    public void connectClient(MessageContext msgContext) {
        try {
            this.clientMessageContext = msgContext;
            clientConnected.set(true);
            
            // Set HTTP headers for SSE streaming
            msgContext.setProperty("HTTP_SC", 200);
            msgContext.setProperty("Content-Type", "text/event-stream");
            msgContext.setProperty("Cache-Control", "no-cache");
            msgContext.setProperty("Connection", "keep-alive");
            msgContext.setProperty("Access-Control-Allow-Origin", "*");
            
            // Send welcome message
            sendSSEEventToClient(new JSONObject()
                .put("message", "Connected to SSE MCP Endpoint")
                .put("endpoint", name));
            
            log.info("SSE client connected to: " + name);
            
        } catch (Exception e) {
            log.error("Error connecting client to: " + name, e);
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
            
            // Format SSE event
            StringBuilder sseEvent = new StringBuilder();
            sseEvent.append("id: ").append(eventId).append("\n");
            sseEvent.append("event: mcp_response\n");
            sseEvent.append("data: ").append(eventData.toString(2)).append("\n\n");
            
            // Get the HTTP transport output info
            Object transportOut = clientMessageContext.getProperty(
                org.apache.axis2.context.MessageContext.TRANSPORT_OUT);
            
            if (transportOut != null) {
                // Set HTTP response headers for SSE
                clientMessageContext.setProperty("HTTP_SC", 200);
                clientMessageContext.setProperty("Content-Type", "text/event-stream");
                clientMessageContext.setProperty("Cache-Control", "no-cache");
                clientMessageContext.setProperty("Connection", "keep-alive");
                
                // Write directly to output stream if available
                try {
                    OutputStream out = null;
                    // Try to get output stream from transport
                    if (transportOut != null && transportOut.getClass().getName().contains("HTTPOutTransportInfo")) {
                        try {
                            java.lang.reflect.Method getOutputStreamMethod = transportOut.getClass().getMethod("getOutputStream");
                            out = (OutputStream) getOutputStreamMethod.invoke(transportOut);
                        } catch (Exception ex) {
                            log.debug("Could not get output stream via reflection", ex);
                        }
                    }
                    
                    if (out != null) {
                        byte[] eventBytes = sseEvent.toString().getBytes(StandardCharsets.UTF_8);
                        out.write(eventBytes);
                        out.flush();
                        
                        log.debug("SSE event sent to client on: " + name + " - Event ID: " + eventId + 
                                  ", Data: " + eventData.toString());
                    } else {
                        log.warn("sendSSEEventToClient: Output stream is null");
                        disconnectClient();
                    }
                } catch (Exception ex) {
                    log.warn("Error writing to output stream", ex);
                    // Fallback: add to message envelope
                    OMFactory omFactory = clientMessageContext.getEnvelope().getOMFactory();
                    OMElement responseElement = omFactory.createOMElement("sseEvent", null);
                    responseElement.setText(sseEvent.toString());
                    clientMessageContext.getEnvelope().getBody().addChild(responseElement);
                }
            } else {
                log.warn("sendSSEEventToClient: TRANSPORT_OUT not available");
                disconnectClient();
            }
            
        } catch (Exception e) {
            log.error("Error sending SSE event", e);
            disconnectClient();
        }
    }

    
    
}
