package com.logsense.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
public class McpChatClientService {
    private static final Logger log = LoggerFactory.getLogger(McpChatClientService.class);

    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;

    @Value("${logsense.server.url:http://localhost:8080}")
    private String serverUrl;

    @Value("${logsense.server.mcp-endpoint:/mcp}")
    private String mcpEndpoint;

    @Value("${logsense.server.api-key:}")
    private String apiKey;

    private McpSyncClient mcpSyncClient;
    private final List<McpSchema.Tool> registeredTools = new ArrayList<>();

    public record PromptSuggestion(String id, String title, String prompt, String badge, String category) {}
    public record ToolInfo(String name, String description, Map<String, Object> inputSchema) {}
    public record ChatResponse(String answer, String toolUsed, Object toolOutput, List<PromptSuggestion> nextSuggestions) {}

    public McpChatClientService(ObjectMapper objectMapper, ObjectProvider<ChatClient> chatClientProvider) {
        this.objectMapper = objectMapper;
        this.chatClient = chatClientProvider.getIfAvailable();
    }

    @PostConstruct
    public void init() {
        connectMcp();
    }

    public synchronized boolean connectMcp() {
        try {
            if (mcpSyncClient != null) {
                try { mcpSyncClient.closeGracefully(); } catch (Exception ignored) {}
            }
            registeredTools.clear();

            var transportBuilder = HttpClientStreamableHttpTransport.builder(serverUrl)
                    .endpoint(mcpEndpoint)
                    .connectTimeout(Duration.ofSeconds(4));

            if (apiKey != null && !apiKey.isBlank()) {
                transportBuilder.httpRequestCustomizer((builder, method, uri, body, ctx) -> builder.header("X-API-Key", apiKey));
            }

            mcpSyncClient = McpClient.sync(transportBuilder.build())
                    .requestTimeout(Duration.ofSeconds(15))
                    .build();

            mcpSyncClient.initialize();
            var listResult = mcpSyncClient.listTools();
            if (listResult != null && listResult.tools() != null) {
                registeredTools.addAll(listResult.tools());
            }
            log.info("Successfully connected to MCP server at {}{} with {} tools", serverUrl, mcpEndpoint, registeredTools.size());
            return true;
        } catch (Exception e) {
            log.warn("MCP connection to {}{} failed: {}", serverUrl, mcpEndpoint, e.getMessage());
            return false;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (mcpSyncClient != null) {
            try { mcpSyncClient.closeGracefully(); } catch (Exception ignored) {}
        }
    }

    public List<ToolInfo> getAvailableTools() {
        if (!registeredTools.isEmpty()) {
            return registeredTools.stream()
                    .map(t -> new ToolInfo(t.name(), t.description(), t.inputSchema()))
                    .toList();
        }
        return List.of(
                new ToolInfo("list_log_sources", "List implemented read-only log connector types", Map.of()),
                new ToolInfo("search_logs", "Search configured log source by keyword, severity, service and trace", Map.of()),
                new ToolInfo("get_error_logs", "Retrieve ERROR/FATAL/CRITICAL log entries", Map.of()),
                new ToolInfo("find_root_cause", "Investigate likely root causes from bounded log evidence", Map.of()),
                new ToolInfo("correlate_logs", "Group events by shared trace ID and request ID", Map.of()),
                new ToolInfo("detect_anomalies", "Identify unusual severity concentration and repeated error signatures", Map.of()),
                new ToolInfo("get_service_health", "Summarize severity distribution and latest observed errors", Map.of())
        );
    }

    public List<PromptSuggestion> getPromptSuggestions() {
        return List.of(
                new PromptSuggestion("payment_1m", "Payment Errors (Last 1m)", "Show me all ERROR and FATAL logs in sample-logs/payment-service.log within 1 minute", "1 min", "diagnostic"),
                new PromptSuggestion("payment_10m", "Payment Errors (Last 10m)", "Show me all ERROR and FATAL logs in sample-logs/payment-service.log in the last 10 minutes", "10 min", "diagnostic"),
                new PromptSuggestion("order_1h", "Checkout Timeout (Last 1h)", "Investigate root cause of downstream timeout in sample-logs/order-service.log within 1 hour", "1 hour", "investigation"),
                new PromptSuggestion("db_1d", "Database Deadlocks (Last 1d)", "Detect deadlock and statement_timeout errors in sample-logs/database-cluster.log in the last 1 day", "1 day", "database"),
                new PromptSuggestion("payment_error", "Payment Pool Timeout", "Show me all ERROR and FATAL logs in sample-logs/payment-service.log", "Payment", "diagnostic"),
                new PromptSuggestion("auth_ratelimit", "Auth Rate Limiting", "Investigate failed authentication and brute force attempts in sample-logs/auth-service.log within 10 minutes", "Auth", "security"),
                new PromptSuggestion("order_failure", "Order Checkout Failures", "Investigate root cause of downstream timeout in sample-logs/order-service.log", "Orders", "investigation"),
                new PromptSuggestion("gateway_504", "Gateway 504 Timeouts", "Analyze 504 Gateway Timeout errors in sample-logs/api-gateway.log within 1 hour", "Gateway", "networking"),
                new PromptSuggestion("k8s_oom", "K8s OOMKilled Pods", "Find OOMKilled events and container crash loops in sample-logs/k8s-pod-events.log in the last 1 day", "Kubernetes", "infrastructure"),
                new PromptSuggestion("notif_quota", "Notification Quota Errors", "Analyze 429 quota and SMTP delivery failures in sample-logs/notification-service.json.log within 10 minutes", "Notifications", "integration"),
                new PromptSuggestion("trace_correlate", "Trace tr-1001 Across Services", "Correlate trace ID tr-1001 across services in sample-logs/payment-service.log", "Traces", "investigation"),
                new PromptSuggestion("sources", "List MCP Log Sources", "Which log connector types and sources are available in LogSense?", "Sources", "discovery")
        );
    }

    public ChatResponse processChat(String userMessage) {
        String trimmed = userMessage == null ? "" : userMessage.trim();
        if (trimmed.isEmpty()) {
            return new ChatResponse("Please ask a question or select a prompt.", null, null, getPromptSuggestions());
        }

        if (mcpSyncClient == null || registeredTools.isEmpty()) {
            connectMcp();
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        String matchedTool = "analyze_logs";
        Map<String, Object> toolArgs = new HashMap<>();
        String defaultType = "FILE";
        String defaultSource = detectSourceFile(trimmed);

        if (lower.contains("source") || lower.contains("connectors")) {
            matchedTool = "list_log_sources";
        } else if (lower.contains("root cause") || lower.contains("why did it fail") || lower.contains("rootcause")) {
            matchedTool = "find_root_cause";
            toolArgs.put("type", defaultType);
            toolArgs.put("source", defaultSource);
            toolArgs.put("incident", trimmed);
            toolArgs.put("limit", 50);
        } else if (lower.contains("trace") || lower.contains("correlat")) {
            matchedTool = "correlate_logs";
            toolArgs.put("type", defaultType);
            toolArgs.put("source", defaultSource);
            toolArgs.put("correlationId", extractTraceId(trimmed));
        } else if (lower.contains("anomal") || lower.contains("signature")) {
            matchedTool = "detect_anomalies";
            toolArgs.put("type", defaultType);
            toolArgs.put("source", defaultSource);
            toolArgs.put("limit", 50);
        } else if (lower.contains("health") || lower.contains("status")) {
            matchedTool = "get_service_health";
            toolArgs.put("type", defaultType);
            toolArgs.put("source", defaultSource);
            toolArgs.put("limit", 50);
        } else if (lower.contains("error") || lower.contains("fatal") || lower.contains("failed") || lower.contains("timeout")) {
            matchedTool = "get_error_logs";
            toolArgs.put("type", defaultType);
            toolArgs.put("source", defaultSource);
            toolArgs.put("limit", 50);
        } else {
            matchedTool = "analyze_logs";
            toolArgs.put("type", defaultType);
            toolArgs.put("source", defaultSource);
            toolArgs.put("question", trimmed);
            toolArgs.put("limit", 50);
        }

        Duration window = detectDuration(trimmed);
        String durationNote = window != null ? formatDurationLabel(window) : null;

        Object toolResult = executeMcpTool(matchedTool, toolArgs);
        if (toolResult instanceof List<?> list && window != null) {
            toolResult = filterByDuration(list, window);
        }

        String answer = null;
        if (chatClient != null) {
            try {
                String systemPrompt = """
                        You are LogSense AI Assistant, an SRE and DevOps expert.
                        An MCP tool was executed on the log system.
                        Review the user question and the tool output, and provide a clear, structured explanation with actionable suggestions.
                        """;
                String prompt = "Question: " + trimmed + (durationNote != null ? " [Time Window: " + durationNote + "]" : "")
                        + "\nMCP Tool: " + matchedTool + "\nTool Output: " + objectMapper.writeValueAsString(toolResult);
                answer = chatClient.prompt().system(systemPrompt).user(prompt).call().content();
            } catch (Exception ex) {
                log.info("ChatClient fallback: {}", ex.getMessage());
            }
        }

        if (answer == null || answer.isBlank()) {
            answer = generateSummary(matchedTool, toolResult, trimmed, durationNote);
        }

        return new ChatResponse(answer, matchedTool, toolResult, getPromptSuggestions());
    }

    private Duration detectDuration(String input) {
        if (input == null) return null;
        String lower = input.toLowerCase(Locale.ROOT);
        // within / last 1 minute(s)
        if (lower.matches(".*\\b(within|last|past|in the last|in)\\s+(1|one)\\s*(m|min|minute|minutes)\\b.*")
                || lower.contains("1 minute") || lower.contains("1 min") || lower.contains("1m")) {
            return Duration.ofMinutes(1);
        }
        // within / last 10 minute(s)
        if (lower.matches(".*\\b(within|last|past|in the last|in)\\s+10\\s*(m|min|minute|minutes)\\b.*")
                || lower.contains("10 minutes") || lower.contains("10 min") || lower.contains("10m")) {
            return Duration.ofMinutes(10);
        }
        // within / last 1 hour(s)
        if (lower.matches(".*\\b(within|last|past|in the last|in)\\s+(1|one)\\s*(h|hr|hour|hours)\\b.*")
                || lower.contains("1 hour") || lower.contains("1 hr") || lower.contains("1h")) {
            return Duration.ofHours(1);
        }
        // within / last 1 day(s) / 24 hours
        if (lower.matches(".*\\b(within|last|past|in the last|in)\\s+(1|one)\\s*(d|day|days)\\b.*")
                || lower.contains("1 day") || lower.contains("24 hours") || lower.contains("today") || lower.contains("1d")) {
            return Duration.ofDays(1);
        }
        return null;
    }

    private String formatDurationLabel(Duration duration) {
        if (duration.toMinutes() == 1) return "Last 1 minute";
        if (duration.toMinutes() == 10) return "Last 10 minutes";
        if (duration.toHours() == 1) return "Last 1 hour";
        if (duration.toDays() == 1) return "Last 1 day (24 hours)";
        return "Within " + duration.toMinutes() + " minutes";
    }

    private List<?> filterByDuration(List<?> list, Duration duration) {
        if (list.isEmpty()) return list;
        java.time.Instant maxTs = null;
        for (Object item : list) {
            java.time.Instant ts = extractTimestamp(item);
            if (ts != null && (maxTs == null || ts.isAfter(maxTs))) {
                maxTs = ts;
            }
        }
        if (maxTs == null) return list;
        java.time.Instant cutoff = maxTs.minus(duration);
        List<Object> filtered = new ArrayList<>();
        for (Object item : list) {
            java.time.Instant ts = extractTimestamp(item);
            if (ts == null || !ts.isBefore(cutoff)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private java.time.Instant extractTimestamp(Object item) {
        if (item instanceof Map<?,?> map) {
            Object ts = map.get("timestamp");
            if (ts instanceof String str) {
                try { return java.time.Instant.parse(str); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private Object executeMcpTool(String toolName, Map<String, Object> args) {
        if (mcpSyncClient != null) {
            try {
                var callRequest = McpSchema.CallToolRequest.builder()
                        .name(toolName)
                        .arguments(args)
                        .build();
                var result = mcpSyncClient.callTool(callRequest);
                if (result != null && result.content() != null && !result.content().isEmpty()) {
                    var first = result.content().get(0);
                    if (first instanceof McpSchema.TextContent textContent) {
                        try {
                            return objectMapper.readValue(textContent.text(), Object.class);
                        } catch (Exception parseEx) {
                            return textContent.text();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Call to MCP tool {} failed: {}", toolName, e.getMessage());
                return Map.of("error", e.getMessage(), "tool", toolName);
            }
        }
        return Map.of("status", "disconnected", "message", "MCP server at " + serverUrl + mcpEndpoint + " is offline or unreachable.");
    }

    private String detectSourceFile(String input) {
        if (input == null) return "sample-logs/payment-service.log";
        String lower = input.toLowerCase(Locale.ROOT);
        if (lower.contains("auth")) return "sample-logs/auth-service.log";
        if (lower.contains("order")) return "sample-logs/order-service.log";
        if (lower.contains("gateway")) return "sample-logs/api-gateway.log";
        if (lower.contains("database") || lower.contains("deadlock")) return "sample-logs/database-cluster.log";
        if (lower.contains("k8s") || lower.contains("pod") || lower.contains("oom")) return "sample-logs/k8s-pod-events.log";
        if (lower.contains("notif") || lower.contains("twilio") || lower.contains("sms") || lower.contains("quota")) return "sample-logs/notification-service.json.log";
        return "sample-logs/payment-service.log";
    }

    private String extractTraceId(String input) {
        var matcher = java.util.regex.Pattern.compile("\\b(tr-[a-zA-Z0-9_-]+|req-[a-zA-Z0-9_-]+)\\b").matcher(input);
        return matcher.find() ? matcher.group(1) : "tr-1001";
    }

    private String generateSummary(String tool, Object result, String query, String durationLabel) {
        StringBuilder sb = new StringBuilder();
        sb.append("### MCP Tool Execution: `").append(tool).append("`\n\n");
        if (durationLabel != null) {
            sb.append("⏱️ **Time Window**: `").append(durationLabel).append("`\n\n");
        }
        if (result instanceof Map<?,?> map && map.containsKey("error")) {
            sb.append("⚠️ **Tool execution error**: ").append(map.get("error")).append("\n\n");
            sb.append("Ensure the LogSense AI server is running on port 8080.");
        } else if (result instanceof List<?> list) {
            sb.append("Retrieved **").append(list.size()).append(" record(s)** from LogSense MCP server.\n\n");
            int count = 0;
            for (Object item : list) {
                if (++count > 5) {
                    sb.append("- *...and ").append(list.size() - 5).append(" more entries.*");
                    break;
                }
                sb.append("- ").append(item.toString()).append("\n");
            }
        } else {
            sb.append("Successfully executed query: *\"").append(query).append("\"*.\n\n");
            sb.append("See raw output below for complete details.");
        }
        return sb.toString();
    }
}
