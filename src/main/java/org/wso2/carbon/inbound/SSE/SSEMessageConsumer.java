package org.wso2.carbon.inbound.SSE;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.wso2.carbon.inbound.endpoint.protocol.generic.GenericInboundListener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE Message Consumer - Listener-based implementation of GenericInboundListener
 */
public class SSEMessageConsumer extends GenericInboundListener {

    private static final Log log = LogFactory.getLog(SSEMessageConsumer.class);
    private static final int MAX_STORED_EVENTS = 1000;

    private final String endpointUrl;
    private final String contentType;
    private final long reconnectIntervalMs;
    private final Map<String, String> headers;
    private final SynapseEnvironment synapseEnvironment;
    private final boolean sequential;

    private final AtomicBoolean listening = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    
    private URLConnection connection;

    /**
     * Constructor called by WSO2 framework
     * GenericInboundListener expects InboundProcessorParams
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
     * Start listening to SSE stream using raw HTTP connection (no Jersey to avoid DI issues)
     */
    public void startListening() {
        if (listening.get()) {
            log.warn("SSE consumer already listening for: " + name);
            return;
        }
        
        listening.set(true);
        new Thread(() -> {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Starting SSE listener thread for: " + name);
                }
                
                URL url = new URL(endpointUrl);
                connection = url.openConnection();
                
                // Set connection timeouts
                connection.setConnectTimeout(10000);  // 10 seconds
                connection.setReadTimeout(300000);    // 5 minutes
                
                // Add custom headers if any
                if (headers != null && !headers.isEmpty()) {
                    for (Map.Entry<String, String> header : headers.entrySet()) {
                        connection.setRequestProperty(header.getKey(), header.getValue());
                    }
                }
                
                // Set Accept header for SSE
                connection.setRequestProperty("Accept", contentType);
                connection.setRequestProperty("Cache-Control", "no-cache");
                connection.setRequestProperty("Connection", "keep-alive");
                
                // Connect and read stream
                InputStream inputStream = connection.getInputStream();
                log.info("Connected to SSE endpoint: " + name + " -> " + endpointUrl);
                processSSEStream(inputStream);
                
            } catch (java.io.EOFException e) {
                log.info("SSE stream ended for: " + name);
                scheduleReconnect();
            } catch (InterruptedIOException e) {
                log.info("SSE listener interrupted for: " + name);
            } catch (Exception e) {
                log.error("Error in SSE listener for: " + name, e);
                scheduleReconnect();
            } finally {
                listening.set(false);
                closeConnection();
            }
        }, "SSE-Listener-" + name).start();
        
        log.info("Started SSE listener thread for: " + name + " -> " + endpointUrl);
    }
    
    /**
     * Process the SSE stream line by line
     */
    private void processSSEStream(InputStream inputStream) throws Exception {
        String line;
        StringBuilder eventData = new StringBuilder();
        String eventId = null;
        
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(inputStream, "UTF-8"))) {
            
            while ((line = reader.readLine()) != null && listening.get()) {
                if (paused.get()) {
                    Thread.sleep(100); // Brief pause if paused
                    continue;
                }
                
                line = line.trim();
                
                // Empty line marks end of event
                if (line.isEmpty()) {
                    if (eventData.length() > 0) {
                        onEvent(eventId, eventData.toString());
                        eventData.setLength(0);
                        eventId = null;
                    }
                    continue;
                }
                
                
                if (line.startsWith(":")) {
                    continue;
                }
                
                // Parse field: value format
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    String field = parts[0].trim();
                    String value = (parts.length > 1 ? parts[1].trim() : "");
                    
                    if ("id".equals(field)) {
                        eventId = value;
                    } else if ("data".equals(field)) {
                        if (eventData.length() > 0) {
                            eventData.append("\\n");
                        }
                        eventData.append(value);
                    } else if ("event".equals(field)) {
                        // Optional: event type
                    }
                }
            }
        } catch (java.io.EOFException e) {
            log.info("SSE stream ended for: " + name);
        } catch (InterruptedIOException e) {
            log.info("SSE listener interrupted for: " + name);
        }
    }

    /**
     * Stop listening to SSE stream
     */
    public void stopListening() {
        listening.set(false);
        closeConnection();
        log.info("Stopped SSE listener for: " + name);
    }
    
    /**
     * Close the HTTP connection
     */
    private void closeConnection() {
        if (connection != null) {
            try {
                if (connection instanceof java.net.HttpURLConnection) {
                    ((java.net.HttpURLConnection) connection).disconnect();
                }
            } catch (Exception e) {
                log.debug("Error closing HTTP connection: " + e.getMessage());
            }
        }
    }

    /**
     * Callback when SSE event is received
     * Events are stored in global InboundEventStore for later access
     */
    private void onEvent(String id, String data) {
        // Skip processing if paused
        if (paused.get()) {
            if (log.isDebugEnabled()) {
                log.debug("SSE endpoint is paused, skipping event for: " + name);
            }
            return;
        }
        
        try {
            log.info("Received SSE event [id=" + id + ", data=" + data + "] for: " + name);
            
            // Store event in global store
            InboundEventStore.add(data);
            
            if (log.isDebugEnabled()) {
                log.debug("Event stored in global store. Total events: " + InboundEventStore.getSize());
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
     * Safely close resources (called when error or completion occurs)
     */
    private void safeCloseEventSource() {
        closeConnection();
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