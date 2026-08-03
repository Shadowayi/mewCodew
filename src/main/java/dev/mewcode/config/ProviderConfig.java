package dev.mewcode.config;

import java.util.Objects;

public record ProviderConfig(
        String name,
        String protocol,
        String model,
        String baseUrl,
        String apiKey,
        boolean thinking) {

    public ProviderConfig {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(apiKey, "apiKey");
    }

    public static ProviderConfig of(String name, String protocol, String model,
                                    String baseUrl, String apiKey) {
        return new ProviderConfig(name, protocol, model, baseUrl, apiKey, false);
    }
}
