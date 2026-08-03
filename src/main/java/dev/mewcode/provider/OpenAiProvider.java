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

public final class OpenAiProvider implements ChatProvider {

    private static final String DONE = "[DONE]";

    private final ObjectMapper mapper = new ObjectMapper();
    private final SseClient sseClient = new SseClient();

    @Override
    public String protocol() {
        return "openai";
    }

    @Override
    public void streamChat(ChatRequest request, StreamHandler handler) {
        try {
            byte[] body = buildBody(request);
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Authorization", "Bearer " + request.apiKey());
            boolean[] done = new boolean[1];
            sseClient.stream(new SseRequest(
                    request.baseUrl() + "/chat/completions", headers, body), payload -> {
                try {
                    onPayload(payload, handler, done);
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
        root.put("stream", true);
        ArrayNode messages = root.putArray("messages");
        for (ChatMessage m : request.messages()) {
            ObjectNode msg = messages.addObject();
            msg.put("role", m.role());
            msg.put("content", m.content());
        }
        return mapper.writeValueAsBytes(root);
    }

    private void onPayload(String payload, StreamHandler handler, boolean[] done) throws JsonProcessingException {
        if (DONE.equals(payload)) {
            completeOnce(done, handler);
            return;
        }
        JsonNode node = mapper.readTree(payload);
        JsonNode choices = node.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode delta = choices.get(0).path("delta");
            JsonNode content = delta.path("content");
            if (content.isTextual() && !content.asText().isEmpty()) {
                handler.onTextDelta(content.asText());
            }
            if (choices.get(0).path("finish_reason").isTextual()) {
                completeOnce(done, handler);
            }
        }
    }

    private static void completeOnce(boolean[] done, StreamHandler handler) {
        if (!done[0]) {
            done[0] = true;
            handler.onComplete();
        }
    }
}
