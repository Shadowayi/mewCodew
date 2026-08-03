package dev.mewcode.config;

import java.util.List;

public record AppConfig(List<ProviderConfig> providers) {

    public AppConfig {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("providers must not be empty");
        }
    }
}
