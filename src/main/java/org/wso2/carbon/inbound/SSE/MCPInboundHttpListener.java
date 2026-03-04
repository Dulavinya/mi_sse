package org.wso2.carbon.inbound.SSE;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.SynapseEnvironment;
import org.wso2.carbon.inbound.endpoint.protocol.http.InboundHttpListener;
import org.json.JSONObject;
import org.json.JSONException;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axis2.context.MessageContext;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Properties;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class MCPInboundHttpListener extends InboundHttpListener {

    private static final Log log = LogFactory.getLog(MCPInboundHttpListener.class);
    private static final String MCP_TOOLS_LOCALENTRY_PARAM = "mcp.tools.localentry";

    private final AtomicBoolean listening = new AtomicBoolean(false);
    private MCPHandler mcpHandler;
    private SynapseConfiguration synapseConfig;
    private String toolsLocalEntryKey;

    public MCPInboundHttpListener(InboundProcessorParams params) {
        super(params);
        this.synapseConfig = params.getSynapseEnvironment() != null ?
                params.getSynapseEnvironment().getSynapseConfiguration() : null;
        this.toolsLocalEntryKey = params.getProperties().getProperty(MCP_TOOLS_LOCALENTRY_PARAM, "mcp_tools");
        this.mcpHandler = new MCPHandler(synapseConfig, toolsLocalEntryKey);
        log.info("MCPInboundHttpListener initialized with InboundProcessorParams");
    }

    public MCPInboundHttpListener(Properties properties, String name, SynapseEnvironment synapseEnvironment,
                                   long scanInterval, String fileName, String aspect, boolean sequential, boolean coordination) {
        super(new InboundProcessorParams());
        this.synapseConfig = synapseEnvironment != null ? synapseEnvironment.getSynapseConfiguration() : null;
        this.toolsLocalEntryKey = properties.getProperty(MCP_TOOLS_LOCALENTRY_PARAM, "mcp_tools");
        this.mcpHandler = new MCPHandler(synapseConfig, toolsLocalEntryKey);
        log.info("MCPInboundHttpListener initialized with 8 parameters - Name: " + name);
    }

    @Override
    public void init() {
        log.info("Initializing MCPInboundHttpListener endpoint");
        try {
            super.init();
            if (mcpHandler == null) {
                mcpHandler = new MCPHandler(synapseConfig, toolsLocalEntryKey);
            }
            listening.set(true);
            log.info("MCPInboundHttpListener endpoint initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize MCPInboundHttpListener", e);
            listening.set(false);
            throw new RuntimeException("Initialization failed", e);
        }
    }

    public void onMessage(MessageContext msgContext) {
        log.debug("onMessage: Received HTTP request on MCP endpoint");

        try {
            if (!listening.get()) {
                log.warn("onMessage: Endpoint not listening");
                sendErrorResponse(msgContext, -32603, "Endpoint not listening");
                return;
            }

            JSONObject requestJson = extractMCPRequestFromMessage(msgContext);
            if (requestJson == null || requestJson.length() == 0) {
                log.warn("onMessage: Empty or invalid request body");
                sendErrorResponse(msgContext, -32700, "Parse error");
                return;
            }

            String method = requestJson.getString("method");
            JSONObject params = requestJson.optJSONObject("params");
            if (params == null) {
                params = new JSONObject();
            }
            long requestId = requestJson.optLong("id", System.currentTimeMillis());

            log.info("onMessage: Processing MCP command - Method: " + method + ", Request ID: " + requestId);

            JSONObject response = handleMCPCommand(method, params);
            if (response == null) {
                response = new JSONObject();
            }

            response.put("id", requestId);
            response.put("jsonrpc", "2.0");

            log.debug("onMessage: MCP command completed - Response: " + response.toString());

            sendMCPResponse(msgContext, response);

        } catch (JSONException e) {
            log.error("onMessage: JSON parsing error", e);
            sendErrorResponse(msgContext, -32700, "Parse error - Invalid JSON");
        } catch (Exception e) {
            log.error("onMessage: Unexpected error processing MCP request", e);
            sendErrorResponse(msgContext, -32603, "Internal error: " + e.getMessage());
        }
    }

    private JSONObject extractMCPRequestFromMessage(MessageContext msgContext) {
        try {
            org.apache.axiom.om.OMNode firstNode = msgContext.getEnvelope().getBody().getFirstOMChild();
            String requestBody = null;

            if (firstNode instanceof OMElement) {
                OMElement bodyElement = (OMElement) firstNode;
                requestBody = bodyElement.getText();
            } else if (firstNode != null) {
                requestBody = firstNode.toString();
            } else {
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
            return new JSONObject();
        }
    }

    public JSONObject handleMCPCommand(String method, JSONObject params) {
        JSONObject response = new JSONObject();

        if (!listening.get()) {
            log.warn("handleMCPCommand: Endpoint not listening");
            response.put("error", new JSONObject()
                    .put("code", -32603)
                    .put("message", "Endpoint not listening"));
            return response;
        }

        try {
            log.debug("handleMCPCommand: Handling MCP command - Method: " + method + ", Params: " + params.toString());

            if (mcpHandler != null) {
                response = mcpHandler.handleCommand(method, params);
            } else {
                log.error("handleMCPCommand: MCPHandler is null");
                response.put("error", new JSONObject()
                        .put("code", -32603)
                        .put("message", "Internal server error: MCPHandler not initialized"));
            }

            log.debug("handleMCPCommand: Response generated - " + response.toString());

        } catch (Exception e) {
            log.error("handleMCPCommand: Error handling MCP command: " + method, e);
            response.put("error", new JSONObject()
                    .put("code", -32603)
                    .put("message", "Internal error: " + e.getMessage()));
        }

        return response;
    }

    private void sendMCPResponse(MessageContext msgContext, JSONObject responseJson) {
        try {
            OMFactory omFactory = org.apache.axiom.om.OMAbstractFactory.getOMFactory();
            OMElement responseElement = omFactory.createOMElement("MCP_Response", null);
            responseElement.setText(responseJson.toString());

            org.apache.axiom.om.OMNode node;
            while ((node = msgContext.getEnvelope().getBody().getFirstOMChild()) != null) {
                node.detach();
            }
            msgContext.getEnvelope().getBody().addChild(responseElement);

            msgContext.setProperty("HTTP_SC", 200);
            msgContext.setProperty("Content-Type", "application/json");

            log.debug("sendMCPResponse: Response sent - " + responseJson.toString());
        } catch (Exception e) {
            log.error("sendMCPResponse: Error sending response", e);
        }
    }

    private void sendErrorResponse(MessageContext msgContext, int errorCode, String errorMessage) {
        try {
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", new JSONObject()
                    .put("code", errorCode)
                    .put("message", errorMessage));
            errorResponse.put("jsonrpc", "2.0");

            sendMCPResponse(msgContext, errorResponse);

        } catch (Exception e) {
            log.error("sendErrorResponse: Error sending error response", e);
        }
    }

    @Override
    public void destroy() {
        log.info("Destroying MCPInboundHttpListener endpoint");
        try {
            listening.set(false);
            super.destroy();
            mcpHandler = null;
            log.info("MCPInboundHttpListener endpoint destroyed");
        } catch (Exception e) {
            log.error("Error destroying MCPInboundHttpListener", e);
        }
    }

    @Override
    public void pause() {
        log.info("Pausing MCPInboundHttpListener endpoint");
        listening.set(false);
    }

    @Override
    public boolean activate() {
        log.info("Activating MCPInboundHttpListener endpoint");
        try {
            if (!listening.get()) {
                listening.set(true);
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to activate MCPInboundHttpListener", e);
            return false;
        }
    }

    @Override
    public boolean deactivate() {
        log.info("Deactivating MCPInboundHttpListener endpoint");
        try {
            listening.set(false);
            return true;
        } catch (Exception e) {
            log.error("Error deactivating MCPInboundHttpListener", e);
            return false;
        }
    }
}
