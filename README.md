# LogSense AI Client (MCP Chatbot GUI)

Standalone Model Context Protocol (MCP) Client and interactive GUI chatbot for LogSense AI.

## Overview
- **Port:** `8081` (avoids colliding with the main LogSense AI server on `8080`)
- **MCP Connection:** Connects over Streamable HTTP (`/mcp`) using `HttpClientStreamableHttpTransport`.
- **UI:** Interactive web chatbot with dynamic diagnostic prompt suggestions, tool introspection sidebar, and expandable tool output cards.

## Quick Start
```bash
# 1. Start the main LogSense AI server on port 8080 (in logsense-ai directory)
mvn spring-boot:run

# 2. Start this standalone client (in logsense-ai-client directory)
mvn spring-boot:run
```

Access the client UI at:
```
http://localhost:8081/
```
