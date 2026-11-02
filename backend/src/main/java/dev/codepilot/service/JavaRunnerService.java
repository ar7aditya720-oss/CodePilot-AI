package dev.codepilot.service;

import dev.codepilot.model.RunResponse;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

@Service
public class JavaRunnerService {
  public RunResponse run(String code, String fileName) {
    if (code == null) throw new IllegalArgumentException("Code is required.");
    if (code.length() > 50000) throw new IllegalArgumentException("Code exceeds 50 KB limit.");
    Path dir = null;
    long start = System.nanoTime();
    try {
      dir = Files.createTempDirectory("codepilot-");
      String safeName = fileName != null && fileName.matches("[A-Za-z0-9_-]+\\.java") ? fileName : "Main.java";
      Files.writeString(dir.resolve(safeName), code, StandardCharsets.UTF_8);\n      Path compileLog = dir.resolve("compile-output.txt");
      Process compile = new ProcessBuilder("javac", safeName)
          .directory(dir.toFile())
          .redirectErrorStream(true)
          .redirectOutput(compileLog.toFile())
          .start();
      boolean compiled = compile.waitFor(12, TimeUnit.SECONDS);
      if (!compiled) {
        compile.destroyForcibly();
        compile.waitFor(2, TimeUnit.SECONDS);
        return new RunResponse("Compilation timed out (12 seconds).", 124, true, elapsed(start));
      }
      String compileOutput = Files.exists(compileLog) ? Files.readString(compileLog, StandardCharsets.UTF_8) : "";
      if (compile.exitValue() != 0) return new RunResponse(compileOutput, compile.exitValue(), false, elapsed(start));

      String className = safeName.substring(0, safeName.length() - 5);
      Path runLog = dir.resolve("program-output.txt");
      Process run = new ProcessBuilder("java", "-Xms16m", "-Xmx128m", "-cp", dir.toString(), className)
          .directory(dir.toFile())
          .redirectErrorStream(true)
          .redirectOutput(runLog.toFile())
          .start();
      boolean finished = run.waitFor(4, TimeUnit.SECONDS);
      if (!finished) {
        run.destroyForcibly();
        run.waitFor(2, TimeUnit.SECONDS);
        String partial = Files.exists(runLog) ? Files.readString(runLog, StandardCharsets.UTF_8) : "";
        return new RunResponse((partial.isBlank() ? "" : partial + System.lineSeparator()) +
            "Execution timed out (4 seconds). This runner is for trusted local development only, not a secure sandbox.", 124, true, elapsed(start));
      }
      String output = Files.exists(runLog) ? Files.readString(runLog, StandardCharsets.UTF_8) : "";
      return new RunResponse(output.isBlank() ? (run.exitValue() == 0 ? "Program finished successfully with no output." : "Program exited with code " + run.exitValue()) : output,
          run.exitValue(), false, elapsed(start));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new RunResponse("Java execution was interrupted.", 1, false, elapsed(start));
    } catch (Exception e) {
      return new RunResponse("Could not run Java code: " + e.getMessage() + ". Install JDK 21 and ensure javac/java are on PATH.", 1, false, elapsed(start));
    } finally {
      if (dir != null) {
        try (var paths = Files.walk(dir)) {
          paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
          });
        } catch (Exception ignored) {}
      }
    }
  }

  private long elapsed(long start) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
  }
}