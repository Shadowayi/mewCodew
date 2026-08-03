package dev.mewcode.chat;

import java.util.Objects;

public record ChatMessage(String role, String content, String thinking) {

    public ChatMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null);
    }

    public static ChatMessage assistant(String content, String thinking) {
        return new ChatMessage("assistant", content, thinking);
    }
}
