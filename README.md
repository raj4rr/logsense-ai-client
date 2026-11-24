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

---

## 20 Sample Diagnostic Prompts (With Duration Filters)

The chatbot automatically parses temporal duration phrases such as **`within 1 minute`**, **`in the last 10 minutes`**, **`within 1 hour`**, or **`in the last 1 day`**, scoping log analysis and MCP diagnostic outputs to the requested time window:

### 1. Error & Incident Investigation (With Durations)
1. **Payment Pool Timeout Errors (Last 1 minute):**
   > `Show me all ERROR and FATAL logs in sample-logs/payment-service.log within 1 minute`
2. **Payment Pool Timeout Errors (Last 10 minutes):**
   > `Show me all ERROR and FATAL logs in sample-logs/payment-service.log in the last 10 minutes`
3. **Order Checkout Failures (Last 1 hour):**
   > `Investigate root cause of downstream timeout in sample-logs/order-service.log within 1 hour`
4. **Gateway 504 Timeouts (Last 10 minutes):**
   > `Analyze 504 Gateway Timeout errors in sample-logs/api-gateway.log in the last 10 minutes`
5. **Gateway 504 Timeouts (Last 1 hour):**
   > `Analyze 504 Gateway Timeout errors in sample-logs/api-gateway.log within 1 hour`
6. **Auth Brute Force & Rate Limiting (Last 10 minutes):**
   > `Investigate failed authentication and brute force attempts in sample-logs/auth-service.log within 10 minutes`
7. **Database Deadlocks & Lock Contention (Last 1 day):**
   > `Detect deadlock and statement_timeout errors in sample-logs/database-cluster.log in the last 1 day`

### 2. Infrastructure & Kubernetes (With Durations)
8. **Kubernetes OOMKilled Pods (Last 10 minutes):**
   > `Find OOMKilled events and container crash loops in sample-logs/k8s-pod-events.log in the last 10 minutes`
9. **Kubernetes OOMKilled Pods (Last 1 day):**
   > `Find OOMKilled events and container crash loops in sample-logs/k8s-pod-events.log in the last 1 day`
10. **Node Disk Pressure & Evictions (Last 1 hour):**
    > `Check node eviction and disk pressure warnings in sample-logs/k8s-pod-events.log within 1 hour`
11. **Failed Pod Health Probes (Last 1 minute):**
    > `Analyze failing liveness and readiness probe events in sample-logs/k8s-pod-events.log within 1 minute`

### 3. Distributed Tracing & Cross-Service Correlation (With Durations)
12. **Trace tr-1001 Across Services (Last 10 minutes):**
    > `Correlate trace ID tr-1001 across services in sample-logs/payment-service.log within 10 minutes`
13. **Order Processing Flow by Trace (Last 1 hour):**
    > `Track trace tr-3001 through order and inventory services in sample-logs/order-service.log within 1 hour`
14. **Security Correlation for Suspicious User (Last 10 minutes):**
    > `Correlate request ID req-2003 for user admin in sample-logs/auth-service.log in the last 10 minutes`

### 4. Integration & Notification Rate Limits (With Durations)
15. **Notification Rate Limit HTTP 429 (Last 1 minute):**
    > `Analyze 429 quota and rate limit errors in sample-logs/notification-service.json.log within 1 minute`
16. **Notification Rate Limit HTTP 429 (Last 10 minutes):**
    > `Analyze 429 quota and rate limit errors in sample-logs/notification-service.json.log in the last 10 minutes`
17. **SMTP Mail Delivery Failures (Last 1 hour):**
    > `Show email delivery failures and SMTP timeout errors in sample-logs/notification-service.json.log within 1 hour`

### 5. Service Health & Anomaly Detection (With Durations)
18. **Service Health Summary (Last 10 minutes):**
    > `Check service health and severity distribution in sample-logs/payment-service.log in the last 10 minutes`
19. **Database Recurring Errors (Last 1 day):**
    > `Detect error concentration and recurring log signatures in sample-logs/database-cluster.log in the last 1 day`

### 6. MCP Discovery & Capabilities
20. **List Registered Log Connectors & Sources:**
    > `Which log connector types and sources are available in LogSense?`
