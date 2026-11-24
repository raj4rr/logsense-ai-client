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

## 20 Sample Diagnostic Prompts

The following prompts can be copied directly into the chatbot input or triggered to exercise all MCP log inspection, analysis, root-cause investigation, and correlation tools against the provided sample logs:

### 1. Error & Incident Investigation
1. **Payment Pool Timeout Errors:**
   > `Show me all ERROR and FATAL logs in sample-logs/payment-service.log`
2. **Order Checkout Failures:**
   > `Investigate root cause of downstream timeout in sample-logs/order-service.log`
3. **Gateway 504 Gateway Timeouts:**
   > `Analyze 504 Gateway Timeout errors in sample-logs/api-gateway.log`
4. **Auth Brute Force & Rate Limiting:**
   > `Investigate failed authentication and brute force attempts in sample-logs/auth-service.log`
5. **Database Deadlocks & Lock Contention:**
   > `Detect deadlock and statement_timeout errors in sample-logs/database-cluster.log`

### 2. Infrastructure & Kubernetes
6. **Kubernetes OOMKilled Pods:**
   > `Find OOMKilled events and container crash loops in sample-logs/k8s-pod-events.log`
7. **Node Disk Pressure & Evictions:**
   > `Check node eviction and disk pressure warnings in sample-logs/k8s-pod-events.log`
8. **Failed Pod Health Probes:**
   > `Analyze failing liveness and readiness probe events in sample-logs/k8s-pod-events.log`
9. **Kubelet Sandbox Failures:**
   > `Show all FailedCreatePodSandBox and image pull backoffs in sample-logs/k8s-pod-events.log`

### 3. Distributed Tracing & Cross-Service Correlation
10. **Trace tr-1001 Across Services:**
    > `Correlate trace ID tr-1001 across services in sample-logs/payment-service.log`
11. **Order Processing Flow by Trace:**
    > `Track trace tr-3001 through order and inventory services in sample-logs/order-service.log`
12. **API Gateway End-to-End Latency:**
    > `Correlate trace tr-4001 between api-gateway and billing service in sample-logs/api-gateway.log`
13. **Security Correlation for Suspicious User:**
    > `Correlate request ID req-2003 for user admin in sample-logs/auth-service.log`

### 4. Integration, Third-Party & JSON Structured Logs
14. **Notification Rate Limit (HTTP 429):**
    > `Analyze 429 quota and rate limit errors in sample-logs/notification-service.json.log`
15. **SMTP Mail Delivery Failures:**
    > `Show email delivery failures and SMTP timeout errors in sample-logs/notification-service.json.log`
16. **SMS Gateway Circuit Breaker:**
    > `Find circuit breaker open events for sms-gateway in sample-logs/notification-service.json.log`

### 5. Service Health & Anomaly Detection
17. **Service Health Summary:**
    > `Check service health and severity distribution in sample-logs/payment-service.log`
18. **Unusual Error Concentrated Anomalies:**
    > `Detect error concentration and recurring log signatures in sample-logs/database-cluster.log`
19. **Authentication Anomaly Detection:**
    > `Detect anomalies in failed login signatures in sample-logs/auth-service.log`

### 6. MCP Discovery & Capabilities
20. **List Registered Log Connectors & Sources:**
    > `Which log connector types and sources are available in LogSense?`
