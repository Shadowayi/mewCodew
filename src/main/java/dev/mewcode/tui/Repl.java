package dev.mewcode.tui;

import dev.mewcode.chat.ChatController;
import dev.mewcode.chat.StreamHandler;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.nio.file.Path;

public final class Repl {

    private static final String PROMPT = "> ";
    private static final String DEFAULT_HISTORY = "~/.mewcode/history";

    public enum Dispatch { EXIT, CLEAR, HELP, UNKNOWN, CHAT }

    private final ChatController controller;
    private final OutputPrinter printer;
    private final boolean[] sawThinking = new boolean[]{false};
    private final boolean[] startedText = new boolean[]{false};

    public Repl(ChatController controller, OutputPrinter printer) {
        this.controller = controller;
        this.printer = printer;
    }

    public void run() throws IOException {
        Terminal terminal = TerminalBuilder.builder().build();
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .variable(LineReader.HISTORY_FILE,
                        DEFAULT_HISTORY.replaceFirst("^~", System.getProperty("user.home")))
                .build();

        printer.printInfo("MewCode 就绪。输入问题开始对话，/help 查看命令。");

        while (true) {
            String line;
            try {
                line = reader.readLine(PROMPT);
            } catch (UserInterruptException e) {
                continue;
            } catch (EndOfFileException e) {
                printer.printLine("");
                break;
            }
            if (line == null) {
                break;
            }
            String trimmed = line.trim();
            switch (dispatch(trimmed)) {
                case EXIT -> {
                    return;
                }
                case CLEAR -> {
                    controller.clear();
                    resetStreamState();
                    printer.printInfo("会话历史已清空。");
                }
                case HELP -> printHelp();
                case UNKNOWN -> printer.printError("未知命令：/" + trimmed.substring(1) + "。/help 查看可用命令。");
                case CHAT -> {
                    printer.printLine("");
                    controller.send(trimmed, view);
                    resetStreamState();
                }
            }
        }
    }

    private void printHelp() {
        printer.printInfo("可用命令：");
        printer.printLine("  /help   显示本帮助");
        printer.printLine("  /clear  清空会话历史");
        printer.printLine("  /exit   退出 MewCode");
        printer.printLine("  其他输入将作为问题发送给模型。");
    }

    public Dispatch dispatch(String line) {
        line = line.trim();
        if (line.startsWith("/")) {
            return switch (line) {
                case "/exit" -> Dispatch.EXIT;
                case "/clear" -> Dispatch.CLEAR;
                case "/help" -> Dispatch.HELP;
                default -> Dispatch.UNKNOWN;
            };
        }
        return Dispatch.CHAT;
    }

    private void resetStreamState() {
        sawThinking[0] = false;
        startedText[0] = false;
    }

    private final StreamHandler view = new StreamHandler() {
        @Override
        public void onThinkingDelta(String delta) {
            sawThinking[0] = true;
            printer.printThinking(delta);
        }

        @Override
        public void onTextDelta(String delta) {
            if (!startedText[0]) {
                startedText[0] = true;
                if (sawThinking[0]) {
                    printer.printSeparator();
                }
            }
            printer.printText(delta);
        }

        @Override
        public void onComplete() {
            printer.printLine("");
            printer.printSeparator();
        }

        @Override
        public void onError(Throwable t) {
            printer.printLine("");
            printer.printError(readableError(t));
        }
    };

    private static String readableError(Throwable t) {
        String msg = t.getMessage();
        return msg == null || msg.isBlank() ? t.getClass().getSimpleName() : msg;
    }
}
