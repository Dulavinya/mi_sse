# SSE Connector - MI Configuration Files

This directory contains the configuration files needed to deploy the SSE inbound connector to WSO2 MI.

## Files

### sse-inbound.xml
The inbound endpoint configuration that defines:
- **Name**: SSEInboundTest
- **Class**: org.wso2.carbon.inbound.SSE.SSEMessageConsumer
- **Endpoint URL**: http://localhost:8080/events (SSE server to connect to)
- **Content Type**: application/json
- **Reconnect Interval**: 5000ms (auto-reconnect on failure)

## Deployment Instructions

### Step 1: Copy to MI
```bash
cp src/main/resources/mi-config/sse-inbound.xml \
   $MI_HOME/repository/deployment/server/synapse-configs/default/inbound-endpoints/
```

Replace `$MI_HOME` with your WSO2 MI installation path.

### Step 2: Restart MI
```bash
cd $MI_HOME/bin
./micro-integrator.sh
```

## Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| endpointUrl | http://localhost:8080/events | SSE server URL to connect to |
| contentType | application/json | Expected content type of events |
| reconnectIntervalMs | 5000 | Wait time before reconnect on failure |
| sequential | true | Process events sequentially |

## How It Works

1. **MI Startup**: When MI starts, the inbound endpoint is loaded
2. **Connection**: SSE connector connects to the configured endpointUrl
3. **Event Listening**: Waits for SSE events from the server
4. **Event Storage**: Each event received is stored in `InboundEventStore`
5. **Auto-Reconnect**: If connection fails, automatically reconnects after `reconnectIntervalMs`

## Testing

To test with the included test server:

```bash
# Terminal 1: Start SSE test server
python3 sse-test-server.py

# Terminal 2: Start MI
cd $MI_HOME/bin
./micro-integrator.sh

# Terminal 3: Monitor MI logs
tail -f $MI_HOME/repository/logs/wso2carbon.log | grep -i sse
```

## Notes

- **No Sequence Injection**: New design stores events in memory, no sequence processing
- **Thread-Safe**: Uses `CopyOnWriteArrayList` for concurrent access
- **Max Events**: Stores up to 1000 events (FIFO overflow)
- **Raw HTTP**: Uses native Java HTTP (no Jersey framework)

## Design Notes

The new SSE connector design is:
- ✅ Lightweight (15KB JAR)
- ✅ No sequence integration
- ✅ No DI framework dependencies
- ✅ Pure event buffering
- ✅ Thread-safe storage
- ✅ Auto-reconnection

See `IMPLEMENTATION.md` and `MI_INTEGRATION_TEST.md` for more details.
