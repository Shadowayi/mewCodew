package dev.mewcode.chat;

import dev.mewcode.config.ProviderConfig;
import dev.mewcode.provider.ChatProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChatController {

    private final ProviderConfig config;
    private final ChatProvider provider;
    private final List<ChatMessage> history = new ArrayList<>();

    public ChatController(ProviderConfig config, ChatProvider provider) {
        this.config = config;
        this.provider = provider;
    }

    public void send(String userInput, StreamHandler view) {
        history.add(ChatMessage.user(userInput));
        ChatRequest request = new ChatRequest(
                config.model(),
                config.baseUrl(),
                config.apiKey(),
                new ArrayList<>(history),
                config.thinking());

        StringBuilder text = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        StreamHandler inner = new StreamHandler() {
            @Override
            public void onThinkingDelta(String delta) {
                thinking.append(delta);
                view.onThinkingDelta(delta);
            }

            @Override
            public void onTextDelta(String delta) {
                text.append(delta);
                view.onTextDelta(delta);
            }

            @Override
            public void onComplete() {
                String thinkingText = thinking.length() == 0 ? null : thinking.toString();
                history.add(ChatMessage.assistant(text.toString(), thinkingText));
                view.onComplete();
            }

            @Override
            public void onError(Throwable t) {
                view.onError(t);
            }
        };

        try {
            provider.streamChat(request, inner);
        } catch (RuntimeException e) {
            view.onError(e);
        }
    }

    public void clear() {
        history.clear();
    }

    public List<ChatMessage> history() {
        return Collections.unmodifiableList(history);
    }
}
