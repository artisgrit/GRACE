package org.grace.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HttpAiProvider implements AiProvider {
    private final ObjectMapper mapper;
    public HttpAiProvider(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public String generate(AiConfiguration config, String systemPrompt, String userPrompt) throws Exception {
        boolean ollama = config.providerType().equals("OLLAMA");
        String path = ollama ? "api/chat" : "v1/chat/completions";
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("model", config.chatModel());
        body.put("messages", List.of(Map.of("role","system","content",systemPrompt), Map.of("role","user","content",userPrompt)));
        body.put("stream", false);
        if (ollama) body.put("options", Map.of("temperature", config.temperature(), "num_predict", config.maxTokens()));
        else { body.put("temperature", config.temperature()); body.put("max_tokens", config.maxTokens()); }
        JsonNode result = send(config, path, body);
        String answer = ollama ? result.path("message").path("content").asText() : result.path("choices").path(0).path("message").path("content").asText();
        if (answer.isBlank()) throw new IllegalStateException("AI provider returned an empty answer");
        return answer;
    }

    @Override
    public List<List<Double>> embed(AiConfiguration config, List<String> inputs) throws Exception {
        boolean ollama = config.providerType().equals("OLLAMA");
        JsonNode result = send(config, ollama ? "api/embed" : "v1/embeddings",
            ollama ? Map.of("model", config.embeddingModel(), "input", inputs) : Map.of("model", config.embeddingModel(), "input", inputs));
        List<List<Double>> vectors = new ArrayList<>();
        JsonNode values = ollama ? result.path("embeddings") : result.path("data");
        values.forEach(value -> {
            JsonNode vector = ollama ? value : value.path("embedding");
            List<Double> numbers = new ArrayList<>(); vector.forEach(number -> numbers.add(number.asDouble())); vectors.add(numbers);
        });
        if (vectors.size() != inputs.size() || vectors.stream().anyMatch(List::isEmpty)) throw new IllegalStateException("Embedding response shape did not match the request");
        return vectors;
    }

    private JsonNode send(AiConfiguration config, String path, Map<String,Object> body) throws Exception {
        URI base = validate(config.baseUrl(), config.allowPrivateNetwork());
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.min(config.timeoutSeconds(), 10)))
            .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(config.timeoutSeconds()))
            .header("Content-Type","application/json").header("Accept","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        if (config.apiKeyEnv() != null && !config.apiKeyEnv().isBlank()) {
            String secret = System.getenv(config.apiKeyEnv());
            if (secret == null || secret.isBlank()) throw new IllegalStateException("Configured AI API key environment variable is not available");
            request.header("Authorization","Bearer " + secret);
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("AI provider returned HTTP " + response.statusCode());
        return mapper.readTree(response.body());
    }

    private static URI validate(String value, boolean allowPrivate) {
        URI uri = URI.create(value.endsWith("/") ? value : value + "/");
        if (uri.getHost()==null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) throw new IllegalArgumentException("AI base URL must be absolute HTTP(S)");
        if (!allowPrivate) try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) throw new IllegalArgumentException("Private AI endpoint requires allowPrivateNetwork=true");
        } catch (java.net.UnknownHostException ex) { throw new IllegalArgumentException("AI provider host could not be resolved"); }
        return uri;
    }
}
