package dev.codepilot.api;

import dev.codepilot.model.ChatRequest;
import dev.codepilot.model.CodeRequest;
import dev.codepilot.model.RunResponse;
import dev.codepilot.service.AiProviderService;
import dev.codepilot.service.CodeReviewService;
import dev.codepilot.service.JavaRunnerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final JavaRunnerService runner; private final CodeReviewService reviewer; private final AiProviderService ai;
  public ApiController(JavaRunnerService runner, CodeReviewService reviewer, AiProviderService ai) { this.runner=runner; this.reviewer=reviewer; this.ai=ai; }
  @GetMapping("/health") public Map<String,Object> health() { return Map.of("status","ok","service","CodePilot AI backend","javaVersion",System.getProperty("java.version")); }
  @PostMapping("/run") public RunResponse run(@Valid @RequestBody CodeRequest r) { try { return runner.run(r.code(),r.fileName()); } catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage()); } }
  @PostMapping("/review") public Map<String,Object> review(@Valid @RequestBody CodeRequest r) { try { return reviewer.review(r.code(),r.fileName(),r.provider()); } catch(IllegalStateException e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,e.getMessage()); } }
  @PostMapping("/chat") public Map<String,Object> chat(@Valid @RequestBody ChatRequest r) { try { return reviewer.chat(r.code(),r.fileName(),r.provider(),r.message()); } catch(IllegalStateException e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,e.getMessage()); } }
  @GetMapping("/ai/providers") public Map<String,Object> providers() { return ai.status(); }
  @GetMapping("/ai/test") public Map<String,Object> test(@RequestParam(defaultValue="local") String provider) { try { return Map.of("ok",true,"response",ai.ask(provider,"Reply with exactly: CodePilot AI connected.")); } catch(IllegalStateException e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,e.getMessage()); } }
}
