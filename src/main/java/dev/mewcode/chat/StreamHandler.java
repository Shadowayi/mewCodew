package dev.mewcode.chat;

public interface StreamHandler {

    void onThinkingDelta(String delta);

    void onTextDelta(String delta);

    void onComplete();

    void onError(Throwable t);
}
