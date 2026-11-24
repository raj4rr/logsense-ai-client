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
                new PromptSuggestion("payment_error", "Payment Pool Timeout", "Show me all ERROR and FATAL logs in sample-logs/payment-service.log", "Payment", "diagnostic"),
                new PromptSuggestion("auth_ratelimit", "Auth Rate Limiting", "Investigate failed authentication and brute force attempts in sample-logs/auth-service.log", "Auth", "security"),
                new PromptSuggestion("order_failure", "Order Checkout Failures", "Investigate root cause of downstream timeout in sample-logs/order-service.log", "Orders", "investigation"),
                new PromptSuggestion("gateway_504", "Gateway 504 Timeouts", "Analyze 504 Gateway Timeout errors in sample-logs/api-gateway.log", "Gateway", "networking"),
                new PromptSuggestion("db_deadlock", "Database Deadlocks", "Detect deadlock and statement_timeout errors in sample-logs/database-cluster.log", "Database", "database"),
                new PromptSuggestion("k8s_oom", "K8s OOMKilled Pods", "Find OOMKilled events and container crash loops in sample-logs/k8s-pod-events.log", "Kubernetes", "infrastructure"),
                new PromptSuggestion("notif_quota", "Notification Quota Errors", "Analyze 429 quota and SMTP delivery failures in sample-logs/notification-service.json.log", "Notifications", "integration"),
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

        Object toolResult = executeMcpTool(matchedTool, toolArgs);

        String answer = null;
        if (chatClient != null) {
            try {
                String systemPrompt = """
                        You are LogSense AI Assistant, an SRE and DevOps expert.
                        An MCP tool was executed on the log system.
                        Review the user question and the tool output, and provide a clear, structured explanation with actionable suggestions.
                        """;
                String prompt = "Question: " + trimmed + "\nMCP Tool: " + matchedTool + "\nTool Output: " + objectMapper.writeValueAsString(toolResult);
                answer = chatClient.prompt().system(systemPrompt).user(prompt).call().content();
            } catch (Exception ex) {
                log.info("ChatClient fallback: {}", ex.getMessage());
            }
        }

        if (answer == null || answer.isBlank()) {
            answer = generateSummary(matchedTool, toolResult, trimmed);
        }

        return new ChatResponse(answer, matchedTool, toolResult, getPromptSuggestions());
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

    private String generateSummary(String tool, Object result, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("### MCP Tool Execution: `").append(tool).append("`\n\n");
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
