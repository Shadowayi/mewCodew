package dev.mewcode.chat;

import java.util.List;
import java.util.Objects;

public record ChatRequest(
        String model,
        String baseUrl,
        String apiKey,
        List<ChatMessage> messages,
        boolean thinking) {

    public ChatRequest {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(messages, "messages");
    }
}
