package com.logsense.client.controller;

import com.logsense.client.service.McpChatClientService;
import com.logsense.client.service.McpChatClientService.ChatResponse;
import com.logsense.client.service.McpChatClientService.PromptSuggestion;
import com.logsense.client.service.McpChatClientService.ToolInfo;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/client")
public class ClientChatController {

    private final McpChatClientService clientService;

    public ClientChatController(McpChatClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping("/prompts")
    public List<PromptSuggestion> getPrompts() {
        return clientService.getPromptSuggestions();
    }

    @GetMapping("/tools")
    public List<ToolInfo> getTools() {
        return clientService.getAvailableTools();
    }

    @PostMapping("/connect")
    public Map<String, Object> reconnect() {
        boolean ok = clientService.connectMcp();
        return Map.of("connected", ok);
    }

    @PostMapping("/message")
    public ChatResponse sendMessage(@RequestBody Map<String, String> body) {
        String msg = body.getOrDefault("message", "");
        return clientService.processChat(msg);
    }
}
