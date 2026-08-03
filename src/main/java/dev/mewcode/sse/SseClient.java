package dev.mewcode.sse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

public final class SseClient {

    private static final String DATA_PREFIX = "data:";

    private final HttpClient httpClient;

    public SseClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public void stream(SseRequest req, Consumer<String> dataConsumer) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(req.uri()))
                .timeout(Duration.ofMinutes(10))
                .POST(HttpRequest.BodyPublishers.ofByteArray(req.body()));
        req.headers().forEach(builder::header);

        HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() >= 400) {
            String detail = readBodySummary(response.body());
            throw new IOException("HTTP " + response.statusCode()
                    + " from " + req.uri() + (detail.isEmpty() ? "" : ": " + detail));
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(DATA_PREFIX)) {
                    String payload = line.substring(DATA_PREFIX.length()).trim();
                    if (!payload.isEmpty()) {
                        dataConsumer.accept(payload);
                    }
                }
            }
        }
    }

    private static String readBodySummary(InputStream body) {
        try (InputStream in = body) {
            byte[] buf = in.readNBytes(2048);
            return new String(buf, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }
}
