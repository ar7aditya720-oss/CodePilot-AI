package dev.codepilot.service;

import dev.codepilot.model.RunResponse;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

@Service
public class JavaRunnerService {
  public RunResponse run(String code, String fileName) {
    if (code.length() > 50000) throw new IllegalArgumentException("Code exceeds 50 KB limit.");
    Path dir = null; long start = System.nanoTime();
    try {
      dir = Files.createTempDirectory("codepilot-");
      String safeName = fileName != null && fileName.matches("[A-Za-z0-9_-]+\\.java") ? fileName : "Main.java";
      Files.writeString(dir.resolve(safeName), code, StandardCharsets.UTF_8);
      Process compile = new ProcessBuilder("javac", safeName).directory(dir.toFile()).redirectErrorStream(true).start();
      boolean compiled = compile.waitFor(12, TimeUnit.SECONDS);
      if (!compiled) { compile.destroyForcibly(); return new RunResponse("Compilation timed out (12 seconds).", 124, true, elapsed(start)); }
      String compileOutput = new String(compile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (compile.exitValue() != 0) return new RunResponse(compileOutput, compile.exitValue(), false, elapsed(start));
      String className = safeName.substring(0, safeName.length() - 5);
      Process run = new ProcessBuilder("java", "-Xms16m", "-Xmx128m", "-cp", dir.toString(), className).directory(dir.toFile()).redirectErrorStream(true).start();
      boolean finished = run.waitFor(4, TimeUnit.SECONDS);
      if (!finished) { run.destroyForcibly(); return new RunResponse("Execution timed out (4 seconds). This runner is for trusted local development only, not a secure sandbox.", 124, true, elapsed(start)); }
      String output = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      return new RunResponse(output.isBlank() ? (run.exitValue() == 0 ? "Program finished successfully with no output." : "Program exited with code " + run.exitValue()) : output, run.exitValue(), false, elapsed(start));
    } catch (Exception e) { return new RunResponse("Could not run Java code: " + e.getMessage() + ". Install JDK 21 and ensure javac/java are on PATH.", 1, false, elapsed(start)); }
    finally { if (dir != null) try (var paths = Files.walk(dir)) { paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} }); } catch (Exception ignored) {} }
  }
  private long elapsed(long start) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start); }
}
