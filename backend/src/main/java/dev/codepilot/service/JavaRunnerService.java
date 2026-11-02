package dev.codepilot.service;

import dev.codepilot.model.RunResponse;
import org.springframework.stereotype.Service;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class JavaRunnerService {
  public RunResponse run(String code, String fileName) {
    return run(code, fileName, "", Map.of());
  }

  public RunResponse run(String code, String fileName, String stdin, Map<String, String> extraFiles) {
    if (code == null) throw new IllegalArgumentException("Code is required.");
    if (code.length() > 50000) throw new IllegalArgumentException("Code exceeds 50 KB limit.");
    if (extraFiles != null && extraFiles.values().stream().mapToInt(v -> v == null ? 0 : v.length()).sum() > 200000)
      throw new IllegalArgumentException("Project source exceeds 200 KB limit.");
    Path dir = null;
    long start = System.nanoTime();
    Process active = null;
    try {
      dir = Files.createTempDirectory("codepilot-");
      String safeName = safeJavaName(fileName) ? fileName : "Main.java";
      Map<String, String> sources = new LinkedHashMap<>();
      if (extraFiles != null) {
        for (var entry : extraFiles.entrySet()) {
          if (!safeJavaName(entry.getKey())) throw new IllegalArgumentException("Invalid Java filename: " + entry.getKey());
          if (entry.getValue() != null) sources.put(entry.getKey(), entry.getValue());
        }
      }
      sources.put(safeName, code);
      if (sources.size() > 20) throw new IllegalArgumentException("Maximum 20 Java files per run.");
      for (var entry : sources.entrySet()) Files.writeString(dir.resolve(entry.getKey()), entry.getValue(), StandardCharsets.UTF_8);

      Path compileLog = dir.resolve("compile-output.txt");
      List<String> compileCommand = new ArrayList<>(List.of("javac", "-encoding", "UTF-8", "-d", dir.toString()));
      compileCommand.addAll(sources.keySet());
      Process compile = new ProcessBuilder(compileCommand).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(compileLog.toFile()).start();
      active = compile;
      boolean compiled = compile.waitFor(15, TimeUnit.SECONDS);
      if (!compiled) {
        compile.destroyForcibly(); compile.waitFor(2, TimeUnit.SECONDS);
        return new RunResponse("Compilation timed out (15 seconds).", 124, true, elapsed(start));
      }
      String compileOutput = Files.exists(compileLog) ? Files.readString(compileLog, StandardCharsets.UTF_8) : "";
      if (compile.exitValue() != 0) return new RunResponse(compileOutput, compile.exitValue(), false, elapsed(start));

      String className = safeName.substring(0, safeName.length() - 5);
      Path runLog = dir.resolve("program-output.txt");
      Process run = new ProcessBuilder("java", "-Xms16m", "-Xmx128m", "-cp", dir.toString(), className)
          .directory(dir.toFile()).redirectErrorStream(true).redirectOutput(runLog.toFile()).start();
      active = run;
      try (OutputStream processInput = run.getOutputStream()) {
        if (stdin != null && !stdin.isEmpty()) processInput.write(stdin.getBytes(StandardCharsets.UTF_8));
        processInput.flush();
      }
      boolean finished = run.waitFor(8, TimeUnit.SECONDS);
      if (!finished) {
        run.destroyForcibly(); run.waitFor(2, TimeUnit.SECONDS);
        String partial = Files.exists(runLog) ? Files.readString(runLog, StandardCharsets.UTF_8) : "";
        return new RunResponse((partial.isBlank() ? "" : partial + System.lineSeparator()) +
            "Execution timed out (8 seconds). This runner is for trusted local development only, not a secure sandbox.", 124, true, elapsed(start));
      }
      String output = Files.exists(runLog) ? Files.readString(runLog, StandardCharsets.UTF_8) : "";
      return new RunResponse(output.isBlank() ? (run.exitValue() == 0 ? "Program finished successfully with no output." : "Program exited with code " + run.exitValue()) : output,
          run.exitValue(), false, elapsed(start));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      if (active != null) active.destroyForcibly();
      return new RunResponse("Java execution was interrupted.", 1, false, elapsed(start));
    } catch (Exception e) {
      return new RunResponse("Could not run Java code: " + e.getMessage() + ". Install JDK 21 and ensure javac/java are on PATH.", 1, false, elapsed(start));
    } finally {
      if (dir != null) {
        try (var paths = Files.walk(dir)) {
          paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        } catch (Exception ignored) {}
      }
    }
  }

  private boolean safeJavaName(String name) {
    return name != null && name.matches("[A-Za-z0-9_-]+\\.java");
  }
  private long elapsed(long start) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start); }
}
