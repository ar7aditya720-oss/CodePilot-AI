package dev.codepilot.service;

import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Service
public class AiProviderService {
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
  public Map<String,Object> status() { return Map.of("local", Map.of("configured", true, "url", System.getenv().getOrDefault("OLLAMA_URL", "http://localhost:11434")), "remote", Map.of("configured", !env("OPENAI_API_KEY").isBlank(), "model", System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini"))); }
  public String ask(String provider, String prompt) {
    try {
      if ("remote".equalsIgnoreCase(provider)) {
        String key = env("OPENAI_API_KEY"); if (key.isBlank()) throw new IllegalStateException("Set OPENAI_API_KEY to use the remote provider.");
        String model = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini");
        String body = "{\"model\":" + q(model) + ",\"messages\":[{\"role\":\"user\",\"content\":" + q(prompt) + "}],\"temperature\":0.2}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(System.getenv().getOrDefault("OPENAI_BASE_URL", "https://api.openai.com/v1") + "/chat/completions")).timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + key).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        String response = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
        return extract(response, "content");
      }
      String url = System.getenv().getOrDefault("OLLAMA_URL", "http://localhost:11434"); String model = System.getenv().getOrDefault("OLLAMA_MODEL", "qwen2.5-coder:7b");
      String body = "{\"model\":" + q(model) + ",\"prompt\":" + q(prompt) + ",\"stream\":false}";
      HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/api/generate")).timeout(Duration.ofSeconds(90)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
      String response = client.send(req, HttpResponse.BodyHandlers.ofString()).body(); return extract(response, "response");
    } catch (IllegalStateException e) { throw e; } catch (Exception e) { throw new IllegalStateException("AI provider request failed: " + e.getMessage() + ". Check provider configuration and that the service is running."); }
  }
  private static String env(String name) { return System.getenv().getOrDefault(name, ""); }
  private static String q(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""; }
  private static String extract(String json, String key) { String marker = "\"" + key + "\":\""; int i=json.indexOf(marker); if(i<0) throw new IllegalStateException("AI response did not contain expected field: " + key); i+=marker.length(); StringBuilder out=new StringBuilder(); boolean esc=false; for(;i<json.length();i++){char c=json.charAt(i);if(esc){out.append(switch(c){case 'n'->'\n';case 'r'->'\r';case 't'->'\t';default->c;});esc=false;}else if(c=='\\')esc=true;else if(c=='\"')break;else out.append(c);} return out.toString(); }
}
