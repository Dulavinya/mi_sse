package org.wso2.carbon.inbound.SSE;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

import java.util.List;

/**
 * Unit tests for InboundEventStore
 * Tests the global, thread-safe event storage
 */
public class InboundEventStoreTest {

    @Before
    public void setUp() {
        InboundEventStore.clear();
    }

    /**
     * Test 1: Add single event
     */
    @Test
    public void testAddEvent() {
        String eventData = "{\"type\": \"test\", \"message\": \"Hello\"}";
        InboundEventStore.add(eventData);
        
        assertEquals("Should have 1 event", 1, InboundEventStore.getSize());
        List<String> events = InboundEventStore.getEvents();
        assertEquals("Event data should match", eventData, events.get(0));
    }

    /**
     * Test 2: Add multiple events
     */
    @Test
    public void testAddMultipleEvents() {
        InboundEventStore.add("{\"event\": \"1\"}");
        InboundEventStore.add("{\"event\": \"2\"}");
        InboundEventStore.add("{\"event\": \"3\"}");
        
        assertEquals("Should have 3 events", 3, InboundEventStore.getSize());
    }

    /**
     * Test 3: Get all events
     */
    @Test
    public void testGetAllEvents() {
        InboundEventStore.add("{\"id\": 1}");
        InboundEventStore.add("{\"id\": 2}");
        
        List<String> events = InboundEventStore.getEvents();
        assertEquals("Should return 2 events", 2, events.size());
    }

    /**
     * Test 4: Get latest N events
     */
    @Test
    public void testGetLatestEvents() {
        for (int i = 1; i <= 10; i++) {
            InboundEventStore.add("{\"id\": " + i + "}");
        }
        
        List<String> latest = InboundEventStore.getLatestEvents(3);
        assertEquals("Should return 3 latest events", 3, latest.size());
    }

    /**
     * Test 5: Get latest events when fewer exist
     */
    @Test
    public void testGetLatestEventsWhenFewerExist() {
        InboundEventStore.add("{\"id\": 1}");
        InboundEventStore.add("{\"id\": 2}");
        
        List<String> latest = InboundEventStore.getLatestEvents(10);
        assertEquals("Should return only 2 events", 2, latest.size());
    }

    /**
     * Test 6: Get size
     */
    @Test
    public void testGetSize() {
        assertEquals("Initially empty", 0, InboundEventStore.getSize());
        
        InboundEventStore.add("{\"data\": \"event1\"}");
        assertEquals("Should have 1 event", 1, InboundEventStore.getSize());
    }

    /**
     * Test 7: Clear events
     */
    @Test
    public void testClearEvents() {
        InboundEventStore.add("{\"id\": 1}");
        InboundEventStore.add("{\"id\": 2}");
        
        assertEquals("Should have 2 events", 2, InboundEventStore.getSize());
        
        InboundEventStore.clear();
        assertEquals("Should be empty after clear", 0, InboundEventStore.getSize());
    }

    /**
     * Test 8: Capacity limit - FIFO overflow
     */
    @Test
    public void testCapacityLimit() {
        // Add 1001 events
        for (int i = 1; i <= 1001; i++) {
            InboundEventStore.add("{\"id\": " + i + "}");
        }
        
        // Should maintain max 1000
        assertEquals("Should not exceed 1000 events", 1000, InboundEventStore.getSize());
        
        // First event should be removed (FIFO)
        List<String> events = InboundEventStore.getEvents();
        assertTrue("First event should be removed", !events.get(0).contains("\"id\": 1"));
    }

    /**
     * Test 9: Ignore null/empty events
     */
    @Test
    public void testIgnoreNullEvents() {
        InboundEventStore.add(null);
        InboundEventStore.add("");
        InboundEventStore.add("{\"id\": 1}");
        
        assertEquals("Should only have 1 valid event", 1, InboundEventStore.getSize());
    }

    /**
     * Test 10: JSON data preservation
     */
    @Test
    public void testJsonDataPreservation() {
        String complexJson = "{\"type\": \"tool_call\", \"tool\": \"database_search\", \"params\": {\"query\": \"SELECT * FROM users\"}, \"timestamp\": 1234567890}";
        InboundEventStore.add(complexJson);
        
        List<String> events = InboundEventStore.getEvents();
        assertEquals("JSON should be preserved exactly", complexJson, events.get(0));
    }

    /**
     * Test 11: Concurrent access - thread safety
     */
    @Test
    public void testConcurrentAccess() throws InterruptedException {
        // Add events from multiple threads
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                InboundEventStore.add("{\"thread\": \"t1\", \"id\": " + i + "}");
            }
        });
        
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                InboundEventStore.add("{\"thread\": \"t2\", \"id\": " + i + "}");
            }
        });
        
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        
        // Should have 200 events without data corruption
        assertEquals("Should have 200 events from 2 threads", 200, InboundEventStore.getSize());
    }

    /**
     * Test 12: Empty list when nothing stored
     */
    @Test
    public void testEmptyList() {
        List<String> events = InboundEventStore.getEvents();
        assertNotNull("Should return empty list, not null", events);
        assertEquals("Should be empty", 0, events.size());
    }

    /**
     * Test 13: Order preservation (FIFO)
     */
    @Test
    public void testFifoOrder() {
        for (int i = 1; i <= 5; i++) {
            InboundEventStore.add("{\"order\": " + i + "}");
        }
        
        List<String> events = InboundEventStore.getEvents();
        
        for (int i = 0; i < events.size(); i++) {
            assertTrue("Event " + (i + 1) + " should be in order", events.get(i).contains("\"order\": " + (i + 1)));
        }
    }

    /**
     * Test 14: Latest events ordering
     */
    @Test
    public void testLatestEventsOrdering() {
        for (int i = 1; i <= 10; i++) {
            InboundEventStore.add("{\"id\": " + i + "}");
        }
        
        List<String> latest = InboundEventStore.getLatestEvents(3);
        
        // Should be [8, 9, 10]
        assertTrue("Should have event 8", latest.get(0).contains("\"id\": 8"));
        assertTrue("Should have event 9", latest.get(1).contains("\"id\": 9"));
        assertTrue("Should have event 10", latest.get(2).contains("\"id\": 10"));
    }

    /**
     * Test 15: Read-only copy returned
     */
    @Test
    public void testReadOnlyCopy() {
        InboundEventStore.add("{\"id\": 1}");
        
        List<String> events1 = InboundEventStore.getEvents();
        InboundEventStore.add("{\"id\": 2}");
        List<String> events2 = InboundEventStore.getEvents();
        
        // events1 should not be affected by new event
        assertEquals("First snapshot should have 1 event", 1, events1.size());
        assertEquals("Second snapshot should have 2 events", 2, events2.size());
    }

    @After
    public void tearDown() {
        InboundEventStore.clear();
    }
}
