# mi-inbound-sse

The SSE (Server-Sent Events) inbound endpoint allows you to receive real-time event streams from SSE-enabled servers through WSO2 Micro Integrator. Server-Sent Events provide a simple and efficient way to push real-time updates from a server to a client over HTTP.

## Features

- Real-time event streaming from SSE endpoints
- Automatic reconnection on connection loss
- Custom header support for authentication
- Configurable content type handling
- Sequential or concurrent message processing

## Configuration Parameters

- **endpointUrl**: The URL of the SSE stream endpoint
- **contentType**: Expected content type of messages (default: application/json)
- **headers**: Custom HTTP headers for authentication/authorization
- **reconnectIntervalMs**: Time to wait before reconnecting (default: 5000ms)
- **readTimeoutMs**: Read timeout in milliseconds (default: 0 - no timeout)

## Usage

Configure the SSE inbound endpoint in your WSO2 MI integration by specifying the endpoint URL and optional parameters like headers and reconnection settings. The endpoint will automatically maintain the connection and inject received events into your configured sequence.
