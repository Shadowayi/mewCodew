package dev.mewcode.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConfigLoader {

    private static final String TEMPLATE_RESOURCE = "/config-template.yaml";

    public static final ConfigLoader INSTANCE = new ConfigLoader();

    private ConfigLoader() {}

    public Path defaultConfigPath() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".mewcode", "config.yaml");
    }

    public AppConfig loadOrDefault() {
        Path file = defaultConfigPath();
        if (!Files.exists(file)) {
            generateTemplate(file);
        }
        return load(file);
    }

    @SuppressWarnings("unchecked")
    public AppConfig load(Path file) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(options);
        try (InputStream in = Files.newInputStream(file)) {
            Map<String, Object> root = yaml.load(in);
            if (root == null || root.isEmpty()) {
                throw new IllegalArgumentException("empty config file: " + file);
            }
            Object raw = root.get("providers");
            if (!(raw instanceof List<?>)) {
                throw new IllegalArgumentException("config must contain a 'providers' list: " + file);
            }
            List<ProviderConfig> providers = new ArrayList<>();
            for (Object item : (List<?>) raw) {
                Map<String, Object> m = (Map<String, Object>) item;
                providers.add(new ProviderConfig(
                        str(m, "name", file),
                        str(m, "protocol", file),
                        str(m, "model", file),
                        str(m, "base_url", file),
                        str(m, "api_key", file),
                        bool(m.get("thinking"))));
            }
            if (providers.isEmpty()) {
                throw new IllegalArgumentException("providers must not be empty: " + file);
            }
            return new AppConfig(providers);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read config: " + file, e);
        }
    }

    public Path generateTemplate(Path file) {
        try (InputStream in = ConfigLoader.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("template resource not found: " + TEMPLATE_RESOURCE);
            }
            Files.createDirectories(file.getParent());
            Files.copy(in, file);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to generate config template: " + file, e);
        }
    }

    public ProviderConfig resolve(AppConfig config, String name) {
        if (name == null || name.isBlank()) {
            return config.providers().get(0);
        }
        return config.providers().stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown provider name: " + name + " (available: "
                        + config.providers().stream().map(ProviderConfig::name).toList() + ")"));
    }

    private static String str(Map<String, Object> m, String key, Path file) {
        Object v = m.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("missing or empty '" + key + "' in provider of " + file);
        }
        return s;
    }

    private static boolean bool(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }
}
