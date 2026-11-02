package dev.codepilot.model;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String code, String fileName, String provider, @NotBlank String message) {
    public ChatRequest { if (fileName == null || !fileName.matches("[A-Za-z0-9_-]+\\.java")) fileName = "Main.java"; if (provider == null || provider.isBlank()) provider = "local"; }
}
