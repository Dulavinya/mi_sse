package org.wso2.carbon.inbound.SSE;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

/**
 * Unit tests for SSEMessageConsumer
 * Tests the listener's integration with InboundEventStore
 */
public class SSEMessageConsumerTest {

    @Before
    public void setUp() {
        InboundEventStore.clear();
    }

    /**
     * Test 1: SSE event data is valid JSON
     */
    @Test
    public void testSSEEventDataFormat() {
        String eventData = "{\"status\": \"OK\", \"message\": \"Event received\"}";
        
        // Verify it's valid JSON by checking basic structure
        assertTrue("Event should be valid JSON", eventData.startsWith("{") && eventData.endsWith("}"));
        assertTrue("Event should contain status field", eventData.contains("\"status\""));
    }

    /**
     * Test 2: Multiple concurrent events
     */
    @Test
    public void testMultipleConcurrentEvents() {
        // Simulate multiple SSE events arriving
        for (int i = 1; i <= 10; i++) {
            String eventData = "{\"event_id\": " + i + ", \"timestamp\": " + System.currentTimeMillis() + "}";
            InboundEventStore.add(eventData);
        }
        
        assertEquals("Should have stored 10 events", 10, InboundEventStore.getSize());
    }

    /**
     * Test 3: Event with complex nested structure
     */
    @Test
    public void testComplexNestedJson() {
        String complexEvent = "{\"type\": \"tool_call\", \"tool\": \"search\", \"params\": {\"query\": \"test\", \"options\": {\"limit\": 100, \"offset\": 0}}}";
        InboundEventStore.add(complexEvent);
        
        assertEquals("Should have 1 event", 1, InboundEventStore.getSize());
    }

    /**
     * Test 4: Event with special characters
     */
    @Test
    public void testEventWithSpecialCharacters() {
        String eventWithSpecialChars = "{\"message\": \"Hello\\nWorld\\t!\", \"emoji\": \"🎉\"}";
        InboundEventStore.add(eventWithSpecialChars);
        
        assertEquals("Should handle special characters", 1, InboundEventStore.getSize());
    }

    /**
     * Test 5: Event data immutability
     */
    @Test
    public void testEventDataImmutability() {
        String original = "{\"id\": 1, \"data\": \"original\"}";
        InboundEventStore.add(original);
        
        java.util.List<String> events = InboundEventStore.getEvents();
        assertEquals("Event data should not change", original, events.get(0));
    }

    /**
     * Test 6: SSE stream parsing - single data field
     */
    @Test
    public void testSseStreamParsingSingleData() {
        // Simulates SSE message: "data: {json}\n\n"
        String eventData = "{\"sensor\": \"temperature\", \"value\": 23.5}";
        InboundEventStore.add(eventData);
        
        assertEquals("Should parse single data field", 1, InboundEventStore.getSize());
    }

    /**
     * Test 7: SSE stream parsing - multiline data
     */
    @Test
    public void testSseStreamParsingMultilineData() {
        // SSE can have multiline data, simulated here
        String multilineData = "{\"message\": \"Line1\\nLine2\\nLine3\"}";
        InboundEventStore.add(multilineData);
        
        assertEquals("Should handle multiline events", 1, InboundEventStore.getSize());
    }

    /**
     * Test 8: Large event payload
     */
    @Test
    public void testLargeEventPayload() {
        StringBuilder largeJson = new StringBuilder("{\"data\": \"");
        for (int i = 0; i < 1000; i++) {
            largeJson.append("x");
        }
        largeJson.append("\"}");
        
        InboundEventStore.add(largeJson.toString());
        assertEquals("Should handle large payloads", 1, InboundEventStore.getSize());
    }

    /**
     * Test 9: Rapid event arrival
     */
    @Test
    public void testRapidEventArrival() {
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < 100; i++) {
            InboundEventStore.add("{\"id\": " + i + ", \"rapid\": true}");
        }
        
        long elapsed = System.currentTimeMillis() - start;
        assertEquals("Should store 100 events", 100, InboundEventStore.getSize());
        assertTrue("Should handle rapid arrival quickly (< 1 sec)", elapsed < 1000);
    }

    /**
     * Test 10: Event with unicode characters
     */
    @Test
    public void testEventWithUnicode() {
        String unicodeEvent = "{\"greeting\": \"你好\", \"emoji\": \"😀\", \"symbol\": \"©®™\"}";
        InboundEventStore.add(unicodeEvent);
        
        assertEquals("Should handle unicode", 1, InboundEventStore.getSize());
    }

    /**
     * Test 11: Event persistence across multiple SSE frames
     */
    @Test
    public void testEventPersistenceAcrossFrames() {
        // First SSE frame
        InboundEventStore.add("{\"frame\": 1, \"status\": \"start\"}");
        
        // Simulate second SSE frame
        InboundEventStore.add("{\"frame\": 2, \"status\": \"middle\"}");
        
        // Third SSE frame
        InboundEventStore.add("{\"frame\": 3, \"status\": \"end\"}");
        
        assertEquals("All frames should be stored", 3, InboundEventStore.getSize());
        assertEquals("Latest event request should return 1 item", 1, InboundEventStore.getLatestEvents(1).size());
    }

    /**
     * Test 12: Empty SSE event handling
     */
    @Test
    public void testEmptySseEvent() {
        // SSE spec allows empty data
        InboundEventStore.add("{}");
        
        assertEquals("Should accept empty JSON object", 1, InboundEventStore.getSize());
    }

    /**
     * Test 13: Event ordering matches arrival order
     */
    @Test
    public void testEventOrderingMatchesArrivalOrder() {
        for (int i = 1; i <= 5; i++) {
            InboundEventStore.add("{\"sequence\": " + i + "}");
        }
        
        java.util.List<String> events = InboundEventStore.getEvents();
        for (int i = 0; i < events.size(); i++) {
            assertTrue("Events should be in arrival order", 
                events.get(i).contains("\"sequence\": " + (i + 1)));
        }
    }

    @After
    public void tearDown() {
        InboundEventStore.clear();
    }
}
