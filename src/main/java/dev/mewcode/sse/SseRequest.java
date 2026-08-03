package dev.mewcode.sse;

import java.util.Map;

public record SseRequest(String uri, Map<String, String> headers, byte[] body) {
}
