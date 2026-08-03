package dev.mewcode;

import dev.mewcode.chat.ChatController;
import dev.mewcode.config.AppConfig;
import dev.mewcode.config.ConfigLoader;
import dev.mewcode.config.ProviderConfig;
import dev.mewcode.provider.ChatProvider;
import dev.mewcode.provider.ProviderFactory;
import dev.mewcode.tui.OutputPrinter;
import dev.mewcode.tui.Repl;

public final class Main {

    private Main() {}

    public static void main(String[] args) {
        String configName = null;
        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                configName = args[++i];
            } else {
                System.err.println("用法：mewcode [--config <provider-name>]");
                System.exit(1);
            }
        }

        try {
            ConfigLoader loader = ConfigLoader.INSTANCE;
            AppConfig appConfig = loader.loadOrDefault();
            ProviderConfig providerConfig = loader.resolve(appConfig, configName);
            ChatProvider provider = ProviderFactory.create(providerConfig);
            ChatController controller = new ChatController(providerConfig, provider);
            new Repl(controller, new OutputPrinter()).run();
        } catch (Exception e) {
            String msg = e.getMessage();
            System.err.println("[!] " + (msg == null || msg.isBlank()
                    ? e.getClass().getSimpleName() : msg));
            System.exit(1);
        }
    }
}
