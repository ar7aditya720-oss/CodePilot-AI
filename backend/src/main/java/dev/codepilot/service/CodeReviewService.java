package dev.codepilot.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class CodeReviewService {
  private final AiProviderService ai;
  public CodeReviewService(AiProviderService ai) { this.ai = ai; }
  public Map<String,Object> review(String code, String fileName, String provider) {
    String prompt = "You are a careful Java code reviewer. Review the code below. Report correctness bugs, edge cases, readability, and time/space complexity only when defensible. Recommend optimization only when there is a meaningful benefit; state trade-offs and avoid invented claims. If no meaningful optimization exists, say so. Return concise Markdown. File: " + fileName + "\n```java\n" + code + "\n```";
    return Map.of("review", ai.ask(provider, prompt), "provider", provider);
  }
  public Map<String,Object> chat(String code, String fileName, String provider, String message) {
    String prompt = "You are CodePilot AI, a helpful Java coding assistant. Answer the user's request with beginner-friendly explanations. Do not claim code was executed unless it was. File: " + fileName + "\nCode:\n```java\n" + code + "\n```\nUser request: " + message;
    return Map.of("answer", ai.ask(provider, prompt), "provider", provider);
  }
}
