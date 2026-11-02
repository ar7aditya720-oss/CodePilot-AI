package dev.codepilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AiProviderService {
  private final HttpClient client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();
  private final ObjectMapper mapper = new ObjectMapper();

  public Map<String, Object> status() {
    Map<String, Object> local = new LinkedHashMap<>();
    local.put("configured", !env("OLLAMA_URL", "http://localhost:11434").isBlank());
    local.put("url", env("OLLAMA_URL", "http://localhost:11434"));
    local.put("model", env("OLLAMA_MODEL", "qwen2.5-coder:7b"));
    Map<String, Object> remote = new LinkedHashMap<>();
    remote.put("configured", !env("OPENAI_API_KEY", "").isBlank());
    remote.put("model", env("OPENAI_MODEL", "gpt-4o-mini"));
    remote.put("baseUrl", env("OPENAI_BASE_URL", "https://api.openai.com/v1"));
    return Map.of("local", local, "remote", remote);
  }

  public String ask(String provider, String prompt) {
    String selected = provider == null || provider.isBlank() ? "local" : provider.trim().toLowerCase();
    return switch (selected) {
      case "local", "ollama" -> askOllama(prompt);
      case "remote", "openai" -> askRemote(prompt);
      default -> throw new IllegalStateException("Unknown AI provider '" + provider + "'. Choose 'local' or 'remote'.");
    };
  }

  private String askOllama(String prompt) {
    String baseUrl = trimSlash(env("OLLAMA_URL", "http://localhost:11434"));
    String model = env("OLLAMA_MODEL", "qwen2.5-coder:7b");
    try {
      String body = mapper.createObjectNode()
          .put("model", model)
          .put("prompt", prompt)
          .put("stream", false)
          .toString();
      HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/generate"))
          .timeout(Duration.ofSeconds(120))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("Ollama returned HTTP " + response.statusCode() + ": " + shorten(response.body()));
      }
      JsonNode json = mapper.readTree(response.body());
      String answer = json.path("response").asText("");
      if (answer.isBlank()) throw new IllegalStateException("Ollama returned an empty response. Check that model '" + model + "' is installed.");
      return answer;
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Could not reach Ollama at " + baseUrl + ". Start Ollama, pull model '" + model + "', and verify OLLAMA_URL. Details: " + e.getMessage());
    }
  }

  private String askRemote(String prompt) {
    String key = env("OPENAI_API_KEY", "");
    if (key.isBlank()) throw new IllegalStateException("Remote AI is not configured. Set OPENAI_API_KEY in the environment used to start the backend, then restart Spring Boot.");
    String baseUrl = trimSlash(env("OPENAI_BASE_URL", "https://api.openai.com/v1"));
    String model = env("OPENAI_MODEL", "gpt-4o-mini");
    try {
      String body = mapper.createObjectNode()
          .put("model", model)
          .set("messages", mapper.createArrayNode()
              .add(mapper.createObjectNode().put("role", "user").put("content", prompt)))
          .toString();
      HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
          .timeout(Duration.ofSeconds(90))
          .header("Authorization", "Bearer " + key)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        String detail = safeRemoteError(response.body());
        throw new IllegalStateException("Remote AI returned HTTP " + response.statusCode() + (detail.isBlank() ? "." : ": " + detail));
      }
      JsonNode json = mapper.readTree(response.body());
      String answer = json.path("choices").path(0).path("message").path("content").asText("");
      if (answer.isBlank()) throw new IllegalStateException("Remote AI returned no message content. Verify OPENAI_MODEL and OPENAI_BASE_URL.");
      return answer;
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Remote AI request failed for " + baseUrl + ". Check network access, OPENAI_BASE_URL, model availability, and API credentials. Details: " + e.getMessage());
    }
  }

  private String safeRemoteError(String body) {
    try {
      JsonNode root = mapper.readTree(body);
      String message = root.path("error").path("message").asText("");
      return message.isBlank() ? shorten(body) : message;
    } catch (Exception ignored) {
      return shorten(body);
    }
  }

  private static String env(String key, String fallback) {
    String value = System.getenv(key);
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static String trimSlash(String value) {
    String result = value.trim();
    while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
    return result;
  }

  private static String shorten(String value) {
    if (value == null) return "";
    String cleaned = value.replaceAll("[\\r\\n\\t]+", " ").trim();
    return cleaned.length() > 400 ? cleaned.substring(0, 400) + "…" : cleaned;
  }
}
