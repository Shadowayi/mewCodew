package dev.mewcode.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.mewcode.chat.ChatMessage;
import dev.mewcode.chat.ChatRequest;
import dev.mewcode.chat.StreamHandler;
import dev.mewcode.sse.SseClient;
import dev.mewcode.sse.SseRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class AnthropicProvider implements ChatProvider {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String THINKING_BETA = "thinking-2024-12-11";

    private final ObjectMapper mapper = new ObjectMapper();
    private final SseClient sseClient = new SseClient();

    @Override
    public String protocol() {
        return "anthropic";
    }

    @Override
    public void streamChat(ChatRequest request, StreamHandler handler) {
        try {
            byte[] body = buildBody(request);
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("x-api-key", request.apiKey());
            headers.put("anthropic-version", ANTHROPIC_VERSION);
            if (request.thinking()) {
                headers.put("anthropic-beta", THINKING_BETA);
            }
            sseClient.stream(new SseRequest(
                    request.baseUrl() + "/v1/messages", headers, body), payload -> {
                try {
                    onPayload(payload, handler);
                } catch (JsonProcessingException e) {
                    handler.onError(e);
                }
            });
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            handler.onError(e);
        }
    }

    private byte[] buildBody(ChatRequest request) throws JsonProcessingException {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", request.model());
        root.put("max_tokens", 8192);
        root.put("stream", true);
        if (request.thinking()) {
            root.set("thinking", mapper.createObjectNode()
                    .put("type", "enabled")
                    .put("budget_tokens", 4096));
        }
        ArrayNode messages = root.putArray("messages");
        for (ChatMessage m : request.messages()) {
            ObjectNode msg = messages.addObject();
            msg.put("role", m.role());
            if ("assistant".equals(m.role()) && m.thinking() != null && !m.thinking().isEmpty()) {
                ArrayNode content = msg.putArray("content");
                content.addObject()
                        .put("type", "thinking")
                        .put("thinking", m.thinking());
                content.addObject()
                        .put("type", "text")
                        .put("text", m.content());
            } else {
                msg.put("content", m.content());
            }
        }
        return mapper.writeValueAsBytes(root);
    }

    private void onPayload(String payload, StreamHandler handler) throws JsonProcessingException {
        JsonNode node = mapper.readTree(payload);
        String type = node.path("type").asText();
        switch (type) {
            case "content_block_delta" -> {
                String deltaType = node.path("delta").path("type").asText();
                switch (deltaType) {
                    case "thinking_delta" -> {
                        String thinking = node.path("delta").path("thinking").asText();
                        if (!thinking.isEmpty()) {
                            handler.onThinkingDelta(thinking);
                        }
                    }
                    case "text_delta" -> {
                        String text = node.path("delta").path("text").asText();
                        if (!text.isEmpty()) {
                            handler.onTextDelta(text);
                        }
                    }
                    default -> {
                    }
                }
            }
            case "message_stop" -> handler.onComplete();
            default -> {
            }
        }
    }
}
