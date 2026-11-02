package dev.codepilot.model;

public record RunResponse(String output, int exitCode, boolean timedOut, long durationMs) {}
