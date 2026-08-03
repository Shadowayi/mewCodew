package dev.mewcode.provider;

import dev.mewcode.chat.ChatRequest;
import dev.mewcode.chat.StreamHandler;

public interface ChatProvider {

    String protocol();

    void streamChat(ChatRequest request, StreamHandler handler);
}
