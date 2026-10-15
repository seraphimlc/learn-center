package com.vlp.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class LlmClient {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${vlp.llm.base-url}")
    private String baseUrl;

    @Value("${vlp.llm.api-key:}")
    private String apiKey;

    @Value("${vlp.llm.model}")
    private String model;

    @Value("${vlp.llm.timeout-seconds:60}")
    private int timeoutSeconds;

    public boolean available() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Optional<String> chat(String system, String user) {
        if (!available()) {
            log.warn("LLM API key missing; skip call");
            return Optional.empty();
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)
                    ),
                    "temperature", 0.4
            );
            String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                log.warn("LLM HTTP {} body={}", resp.statusCode(), truncate(resp.body()));
                return Optional.empty();
            }
            JsonNode root = mapper.readTree(resp.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                log.warn("LLM empty content: {}", truncate(resp.body()));
                return Optional.empty();
            }
            return Optional.of(content.asText());
        } catch (Exception e) {
            log.warn("LLM call failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
