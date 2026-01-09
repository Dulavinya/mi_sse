package org.wso2.carbon.inbound.SSE;

public final class SSEConstants {

    // Inbound endpoint parameters
    public static final String ENDPOINT_URL = "endpointUrl";
    public static final String CONTENT_TYPE = "contentType";
    public static final String HEADERS = "headers"; // comma separated: "Header1=Value1,Header2=Value2"
    public static final String RECONNECT_INTERVAL_MS = "reconnectIntervalMs";
    public static final String READ_TIMEOUT_MS = "readTimeoutMs";
    public static final String SEQUENTIAL = "sequential";
    
    // Default values
    public static final String DEFAULT_CONTENT_TYPE = "application/json";
    public static final long DEFAULT_RECONNECT_INTERVAL_MS = 5000L;
    public static final long DEFAULT_READ_TIMEOUT_MS = 0L; // 0 = no read timeout
    
    // Message context properties
    public static final String MESSAGE_ID = "SseMessageId";
    public static final String MESSAGE_DATA = "SseMessageData";
    public static final String MESSAGE_COMMENT = "SseMessageComment";
    public static final String MESSAGE_LAST_EVENT_ID = "SseLastEventId";
    
    // Control properties
    public static final String SET_ROLLBACK_ONLY = "SET_ROLLBACK_ONLY";
    
    // Private constructor to prevent instantiation
    private SSEConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}