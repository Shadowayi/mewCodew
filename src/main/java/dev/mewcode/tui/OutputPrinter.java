package dev.mewcode.tui;

import java.io.PrintStream;

public final class OutputPrinter {

    private static final String ANSI_RESET = "\u001b[0m";
    private static final String ANSI_GRAY_ITALIC = "\u001b[90m\u001b[3m";
    private static final String ANSI_RED = "\u001b[31m";
    private static final String ANSI_CYAN = "\u001b[36m";

    private final PrintStream out;

    public OutputPrinter() {
        this(System.out);
    }

    public OutputPrinter(PrintStream out) {
        this.out = out;
    }

    public void printThinking(String s) {
        out.print(ANSI_GRAY_ITALIC + s + ANSI_RESET);
        out.flush();
    }

    public void printText(String s) {
        out.print(s);
        out.flush();
    }

    public void printLine(String s) {
        out.println(s);
    }

    public void printInfo(String s) {
        out.println(ANSI_CYAN + "[i] " + s + ANSI_RESET);
    }

    public void printError(String s) {
        out.println(ANSI_RED + "[!] " + s + ANSI_RESET);
    }

    public void printSeparator() {
        out.println(ANSI_CYAN + "---" + ANSI_RESET);
    }
}
