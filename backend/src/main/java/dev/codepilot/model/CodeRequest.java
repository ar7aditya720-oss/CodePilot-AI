package dev.codepilot.model;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record CodeRequest(@NotBlank String code, String fileName, String provider, String stdin, Map<String, String> files) {
    public CodeRequest {
        if (fileName == null || !fileName.matches("[A-Za-z0-9_-]+\\.java")) fileName = "Main.java";
        if (provider == null || provider.isBlank()) provider = "local";
        if (stdin == null) stdin = "";
        if (files == null) files = Map.of();
    }
}
