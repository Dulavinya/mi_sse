#!/usr/bin/env python3
import http.server
import socketserver
import time
import json
import sys

PORT = 8080
EVENT_ID = 1

class SSEHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/events':
            print(f"[{time.strftime('%H:%M:%S')}] New SSE client connected from {self.client_address[0]}")
            
            self.send_response(200)
            self.send_header('Content-Type', 'text/event-stream')
            self.send_header('Cache-Control', 'no-cache')
            self.send_header('Connection', 'keep-alive')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            
            # Send test events
            global EVENT_ID
            for i in range(10):  # Send 10 events
                try:
                    event_data = {
                        "timestamp": time.time(),
                        "event_number": EVENT_ID,
                        "message": f"Test SSE Event #{EVENT_ID}",
                        "data": f"Sample data {EVENT_ID}"
                    }
                    
                    # Send SSE format
                    event_str = f"id: {EVENT_ID}\n"
                    event_str += f"data: {json.dumps(event_data)}\n\n"
                    
                    self.wfile.write(event_str.encode('utf-8'))
                    print(f"[{time.strftime('%H:%M:%S')}] Sent event #{EVENT_ID}: {event_data['message']}")
                    
                    EVENT_ID += 1
                    time.sleep(2)  # Send event every 2 seconds
                    
                except Exception as e:
                    print(f"[{time.strftime('%H:%M:%S')}] Error sending event: {e}")
                    break
            
            print(f"[{time.strftime('%H:%M:%S')}] SSE stream ended")
        else:
            self.send_response(404)
            self.end_headers()

if __name__ == '__main__':
    try:
        with socketserver.TCPServer(("", PORT), SSEHandler) as httpd:
            
            print("SSE Test Server - Sends Test Events")
            
            print(f"✓ SSE Server running on http://localhost:{PORT}/events")
            print("  Press Ctrl+C to stop")
            print()
            httpd.serve_forever()
    except OSError as e:
        if "Address already in use" in str(e):
            print(f"✗ Port {PORT} is already in use")
            print(f"  Kill: lsof -i :{PORT} | awk 'NR!=1 {{print $2}}' | xargs kill -9")
        else:
            print(f"✗ Error: {e}")
        sys.exit(1)
