package org.wso2.carbon.inbound.SSE;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.builder.Builder;
import org.apache.axis2.builder.BuilderUtil;
import org.apache.axis2.builder.SOAPBuilder;
import org.apache.axis2.transport.TransportUtils;
import org.apache.commons.io.input.AutoCloseInputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.wso2.carbon.inbound.endpoint.protocol.generic.GenericInboundListener;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE Message Consumer - Direct implementation of GenericInboundListener
 * This is used directly in the inbound endpoint configuration
 */
public class SSEMessageConsumer extends GenericInboundListener {

    private static final Log log = LogFactory.getLog(SSEMessageConsumer.class);

    private final String endpointUrl;
    private final String contentType;
    private final long reconnectIntervalMs;
    private final Map<String, String> headers;
    private final SynapseEnvironment synapseEnvironment;
    private final boolean sequential;

    private Client client;
    private SseEventSource eventSource;
    private final AtomicBoolean listening = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);

    /**
     * Constructor called by WSO2 framework
     * @param params InboundProcessorParams containing all configuration
     */
    public SSEMessageConsumer(InboundProcessorParams params) {
        super(params);
        
        Properties properties = params.getProperties();
        this.synapseEnvironment = params.getSynapseEnvironment();
        
        this.endpointUrl = properties.getProperty(SSEConstants.ENDPOINT_URL);
        if (this.endpointUrl == null || this.endpointUrl.isEmpty()) {
            throw new SynapseException("SSE endpoint URL (endpointUrl) is required for SSE inbound: " + name);
        }
        
        this.contentType = properties.getProperty(SSEConstants.CONTENT_TYPE, SSEConstants.DEFAULT_CONTENT_TYPE);
        this.reconnectIntervalMs = Long.parseLong(properties.getProperty(SSEConstants.RECONNECT_INTERVAL_MS,
                String.valueOf(SSEConstants.DEFAULT_RECONNECT_INTERVAL_MS)));
        this.headers = parseHeaders(properties.getProperty(SSEConstants.HEADERS));
        this.sequential = Boolean.parseBoolean(properties.getProperty(SSEConstants.SEQUENTIAL, "true"));
        
        log.info("Initialized SSE inbound endpoint: " + name + " -> " + endpointUrl);
    }

    /**
     * Initialize and start the SSE listener
     * Called by WSO2 framework when the inbound endpoint is deployed
     */
    public void init() {
        log.info("init() called for SSE inbound endpoint: " + name);
        startListening();
    }

    /**
     * Destroy and cleanup resources
     * Called by WSO2 framework when the inbound endpoint is undeployed
     */
    public void destroy() {
        log.info("destroy() called for SSE inbound endpoint: " + name);
        stopListening();
    }

    /**
     * Pause processing of SSE events (but keep connection open)
     */
    public void pause() {
        log.info("Pausing SSE inbound endpoint: " + name);
        paused.set(true);
    }

    /**
     * Resume/activate the SSE listener
     */
    public boolean activate() {
        log.info("Activating SSE inbound endpoint: " + name);
        paused.set(false);
        if (!listening.get()) {
            startListening();
        }
        return true;
    }

    /**
     * Deactivate and stop the SSE listener
     */
    public boolean deactivate() {
        log.info("Deactivating SSE inbound endpoint: " + name);
        stopListening();
        return true;
    }

    /**
     * Check if the listener is deactivated
     */
    public boolean isDeactivated() {
        return !listening.get();
    }

    /**
     * Start listening to SSE stream
     */
    public void startListening() {
        if (listening.get()) {
            log.warn("SSE consumer already listening for: " + name);
            return;
        }
        
        try {
            client = ClientBuilder.newBuilder().build();
            
            Invocation.Builder invocationBuilder = client.target(endpointUrl).request();
            
            // Add custom headers if any
            if (headers != null && !headers.isEmpty()) {
                headers.forEach(invocationBuilder::header);
            }
            
            eventSource = SseEventSource.target(client.target(endpointUrl)).build();
            eventSource.register(this::onEvent, this::onError, this::onComplete);
            eventSource.open();
            
            listening.set(true);
            log.info("Started SSE listener for: " + name + " -> " + endpointUrl);
        } catch (Exception e) {
            log.error("Failed to start SSE listener for: " + name, e);
            scheduleReconnect();
        }
    }

    /**
     * Stop listening to SSE stream
     */
    public void stopListening() {
        listening.set(false);
        
        try {
            if (eventSource != null && eventSource.isOpen()) {
                eventSource.close();
            }
        } catch (Exception e) {
            log.warn("Error closing SSE event source for: " + name, e);
        }
        
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            log.warn("Error closing SSE client for: " + name, e);
        }
        
        log.info("Stopped SSE listener for: " + name);
    }

    /**
     * Callback when SSE event is received
     */
    private void onEvent(InboundSseEvent inboundEvent) {
        // Skip processing if paused
        if (paused.get()) {
            if (log.isDebugEnabled()) {
                log.debug("SSE endpoint is paused, skipping event for: " + name);
            }
            return;
        }
        
        try {
            String id = inboundEvent.getId();
            String data = inboundEvent.readData();
            String comment = inboundEvent.getComment();
            
            if (log.isDebugEnabled()) {
                log.debug("Received SSE event [id=" + id + ", data=" + data + "] for: " + name);
            }
            
            // Create message context
            MessageContext msgCtx = createMessageContext();
            
            // Set SSE-specific properties
            msgCtx.setProperty(SSEConstants.MESSAGE_ID, id);
            msgCtx.setProperty(SSEConstants.MESSAGE_DATA, data);
            msgCtx.setProperty(SSEConstants.MESSAGE_COMMENT, comment);
            msgCtx.setProperty(SynapseConstants.IS_INBOUND, true);
            msgCtx.setProperty(SynapseConstants.INBOUND_ENDPOINT_NAME, name);
            
            // Inject message into sequence
            boolean consumed = injectMessage(data, contentType, msgCtx);
            if (!consumed) {
                log.error("Message processing failed for SSE event id=" + id);
            }
        } catch (Exception e) {
            log.error("Error handling SSE event for: " + name, e);
        }
    }

    /**
     * Callback when SSE stream encounters an error
     */
    private void onError(Throwable t) {
        log.error("SSE error for: " + name, t);
        safeCloseEventSource();
        
        if (!paused.get()) {
            scheduleReconnect();
        }
    }

    /**
     * Callback when SSE stream completes
     */
    private void onComplete() {
        log.info("SSE stream completed for: " + name);
        safeCloseEventSource();
        
        if (!paused.get()) {
            scheduleReconnect();
        }
    }

    /**
     * Safely close event source and client
     */
    private void safeCloseEventSource() {
        try {
            if (eventSource != null && eventSource.isOpen()) {
                eventSource.close();
            }
        } catch (Exception e) {
            log.warn("Error while closing event source", e);
        }
        
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            log.warn("Error while closing client", e);
        }
        
        listening.set(false);
    }

    /**
     * Schedule reconnection after error or completion
     */
    private void scheduleReconnect() {
        new Thread(() -> {
            try {
                log.info("Scheduling reconnect in " + reconnectIntervalMs + "ms for: " + name);
                Thread.sleep(reconnectIntervalMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            
            if (!listening.get() && !paused.get()) {
                log.info("Attempting to reconnect SSE for: " + name);
                startListening();
            }
        }, "sse-reconnect-" + name + "-" + UUID.randomUUID()).start();
    }

    /**
     * Inject the SSE message data into the mediation sequence
     */
    private boolean injectMessage(String data, String contentType, MessageContext msgCtx) {
        boolean isConsumed = true;
        
        try {
            if (log.isDebugEnabled()) {
                log.debug("Processing SSE message with Content-Type: " + contentType + " for " + name);
            }
            
            org.apache.axis2.context.MessageContext axis2MsgCtx =
                    ((Axis2MessageContext) msgCtx).getAxis2MessageContext();

            if (contentType == null || contentType.isEmpty()) {
                log.warn("Content type not specified, defaulting to application/json for " + name);
                contentType = "application/json";
            }
            
            // Extract base content type (remove charset etc.)
            int index = contentType.indexOf(';');
            String type = index > 0 ? contentType.substring(0, index) : contentType;

            // Get appropriate builder for content type
            Object builder = BuilderUtil.getBuilderFromSelector(type, axis2MsgCtx);
            if (builder == null) {
                if (log.isDebugEnabled()) {
                    log.debug("No message builder found for type '" + type + "'. Falling back to SOAP for " + name);
                }
                builder = new SOAPBuilder();
            }

            // Build the message
            InputStream in = new AutoCloseInputStream(new ByteArrayInputStream(data.getBytes()));
            OMElement documentElement = ((Builder) builder).processDocument(in, contentType, axis2MsgCtx);
            msgCtx.setEnvelope(TransportUtils.createSOAPEnvelope(documentElement));
            
            // Validate injecting sequence
            if (this.injectingSequence == null || "".equals(this.injectingSequence)) {
                log.error("Injecting sequence not specified for: " + name);
                return false;
            }
            
            // Get the sequence mediator
            SequenceMediator seq = (SequenceMediator) this.synapseEnvironment.getSynapseConfiguration()
                    .getSequence(this.injectingSequence);
            
            if (seq == null) {
                throw new SynapseException(
                        "Sequence '" + this.injectingSequence + "' not found for inbound: " + name);
            }
            
            // Set error handler
            seq.setErrorHandler(this.onErrorSequence);
            
            if (log.isDebugEnabled()) {
                log.debug("Injecting message to sequence: " + this.injectingSequence + " for " + name);
            }
            
            // Inject into sequence
            if (!this.synapseEnvironment.injectInbound(msgCtx, seq, this.sequential)) {
                isConsumed = false;
            }
            
            // Check for rollback
            if (isRollback(msgCtx)) {
                log.warn("Message marked for rollback for: " + name);
                isConsumed = false;
            }
            
        } catch (Exception e) {
            log.error("Error processing SSE message. Expected format: " + contentType, e);
            isConsumed = false;
        }
        
        return isConsumed;
    }

    /**
     * Create a new Synapse MessageContext
     */
    private MessageContext createMessageContext() {
        MessageContext msgCtx = synapseEnvironment.createMessageContext();
        org.apache.axis2.context.MessageContext axis2MsgCtx =
                ((Axis2MessageContext) msgCtx).getAxis2MessageContext();
        axis2MsgCtx.setServerSide(true);
        axis2MsgCtx.setMessageID(UUID.randomUUID().toString());
        return msgCtx;
    }

    /**
     * Check if message is marked for rollback
     */
    private boolean isRollback(MessageContext msgCtx) {
        Object rollbackProp = msgCtx.getProperty(SSEConstants.SET_ROLLBACK_ONLY);
        if (rollbackProp != null) {
            return (rollbackProp instanceof Boolean && ((Boolean) rollbackProp)) ||
                    (rollbackProp instanceof String && Boolean.parseBoolean((String) rollbackProp));
        }
        return false;
    }

    /**
     * Parse header string into map
     * Format: "key1=value1,key2=value2"
     */
    private Map<String, String> parseHeaders(String raw) {
        Map<String, String> map = new java.util.HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        
        String[] pairs = raw.split(",");
        for (String p : pairs) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }
}