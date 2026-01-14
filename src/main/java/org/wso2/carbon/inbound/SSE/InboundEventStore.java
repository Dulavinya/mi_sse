package org.wso2.carbon.inbound.SSE;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Global, thread-safe in-memory store for all inbound SSE events.
 * 

 * 

 */
public class InboundEventStore {
    
    private static final Log log = LogFactory.getLog(InboundEventStore.class);
    private static final int MAX_EVENTS = 1000;
    
    
    private static final CopyOnWriteArrayList<String> eventBuffer = new CopyOnWriteArrayList<>();
    
    /**
     * Add an event to the store.
     * Called by SSE listeners when events arrive.
     * 
     * @param eventData Raw event data (typically JSON string)
     */
    public static void add(String eventData) {
        if (eventData == null || eventData.isEmpty()) {
            return;
        }
        
        // Maintain capacity: remove oldest if at limit
        if (eventBuffer.size() >= MAX_EVENTS) {
            eventBuffer.remove(0);
            if (log.isDebugEnabled()) {
                log.debug("Event store at capacity (1000), removed oldest event (FIFO)");
            }
        }
        
        eventBuffer.add(eventData);
        
        if (log.isDebugEnabled()) {
            log.debug("Event stored. Total events in store: " + eventBuffer.size());
        }
    }
    
    /**
     * Get all stored events (read-only copy).
     * Safe for concurrent iteration.
     * 
     * @return List of all events currently in store
     */
    public static List<String> getEvents() {
        return new ArrayList<>(eventBuffer);
    }
    
    /**
     * Get the latest N events.
     * 
     * @param count Number of recent events to retrieve
     * @return Most recent events (up to count), in order
     */
    public static List<String> getLatestEvents(int count) {
        int size = eventBuffer.size();
        if (size == 0) {
            return Collections.emptyList();
        }
        
        int fromIndex = Math.max(0, size - count);
        return new ArrayList<>(eventBuffer.subList(fromIndex, size));
    }
    
    /**
     * Get event count.
     * 
     * @return Number of events currently stored
     */
    public static int getSize() {
        return eventBuffer.size();
    }
    
    /**
     * Clear all events (mainly for testing).
     */
    public static void clear() {
        eventBuffer.clear();
        log.info("Inbound event store cleared");
    }
}
