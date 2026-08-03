package dev.mewcode.provider;

import dev.mewcode.config.ProviderConfig;

public final class ProviderFactory {

    private ProviderFactory() {}

    public static ChatProvider create(ProviderConfig config) {
        return switch (config.protocol()) {
            case "anthropic" -> new AnthropicProvider();
            case "openai" -> new OpenAiProvider();
            default -> throw new IllegalArgumentException(
                    "unknown protocol: " + config.protocol()
                    + " (supported: anthropic, openai)");
        };
    }
}
