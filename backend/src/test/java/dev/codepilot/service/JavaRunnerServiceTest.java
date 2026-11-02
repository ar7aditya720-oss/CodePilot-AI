package dev.codepilot.service;

import dev.codepilot.model.RunResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JavaRunnerServiceTest {
    private final JavaRunnerService runner = new JavaRunnerService();

    @Test
    void compilesAndRunsValidJavaProgram() {
        String code = "public class Main { public static void main(String[] args) { System.out.println(\"Hello, CodePilot AI!\"); } }";

        RunResponse result = runner.run(code, "Main.java");

        assertEquals(0, result.exitCode());
        assertFalse(result.timedOut());
        assertTrue(result.output().contains("Hello, CodePilot AI!"), result.output());
    }

    @Test
    void returnsCompilerErrorForInvalidJava() {
        String code = "public class Main { public static void main(String[] args) { System.out.println( ; } }";

        RunResponse result = runner.run(code, "Main.java");

        assertNotEquals(0, result.exitCode());
        assertFalse(result.timedOut());
        assertFalse(result.output().isBlank());
    }

    @Test
    void rejectsSourceLargerThanLimit() {
        String code = "x".repeat(50_001);

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> runner.run(code, "Main.java")
        );

        assertTrue(error.getMessage().contains("50 KB"));
    }

    @Test
    void replacesUnsafeJavaFileNameWithMain() {
        String code = "public class Main { public static void main(String[] args) { System.out.println(\"safe\"); } }";

        RunResponse result = runner.run(code, "../Main.java");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("safe"), result.output());
    }
}
