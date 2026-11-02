# CodePilot AI

An AI-assisted Java coding workspace with a React + Monaco editor and a Spring Boot backend.

## Status

MVP source scaffold. AI providers require configuration. Java execution is intended for local development only and is not a secure sandbox.

## Requirements
- Node.js 20+
- Java 21+
- Maven 3.9+
- Optional: Ollama for local AI

## Run locally

1. Start backend: `cd backend` then `mvn spring-boot:run`
2. In another terminal: `npm install` then `npm run dev`
3. Open the Vite URL shown in the terminal.

See `backend/.env.example` for AI provider configuration.