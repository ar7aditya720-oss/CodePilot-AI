# CodePilot AI 🚀

A Java-focused coding workspace with a React + Monaco editor, Spring Boot API, local Ollama support, and an optional OpenAI-compatible remote provider.

## Features
- Monaco Java editor with editable starter code
- Run Java code through the backend compiler/runner
- AI code review covering correctness, edge cases, complexity, and meaningful optimizations
- Local AI via Ollama or remote AI via an OpenAI-compatible chat-completions endpoint
- Health and provider test endpoints

## Requirements
- Node.js 20+
- JDK 21 (both `java` and `javac` on PATH)
- Maven 3.9+
- Optional: Ollama and the `qwen2.5-coder:7b` model

## Start locally

### 1. Configure AI (optional until using Review)

Copy `backend/.env.example` to your own environment configuration and set variables in the shell used to start the backend. Never commit API keys.

For local AI, install Ollama and run:

```powershell
ollama pull qwen2.5-coder:7b
```

For remote AI, set `OPENAI_API_KEY` and optionally `OPENAI_MODEL` / `OPENAI_BASE_URL` in the environment before starting Spring Boot.

### 2. Start backend

```powershell
cd backend
mvn spring-boot:run
```

Backend base URL: `http://localhost:8080`.

### 3. Start frontend in a second terminal

From the repository root:

```powershell
npm install
npm run dev
```

Open the Vite URL printed in the terminal, usually `http://localhost:5173`. The Vite dev server proxies `/api` requests to port 8080.

You can also run `./start-dev.ps1` from PowerShell at the repository root.

## API endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/health` | Backend health/version |
| POST | `/api/run` | Compile and run Java source |
| POST | `/api/review` | Review code using selected AI provider |
| POST | `/api/chat` | Ask a question about current code |
| GET | `/api/ai/providers` | Provider configuration status |
| GET | `/api/ai/test?provider=local` | Test local or remote provider |

Example review request:

```json
{"fileName":"Main.java","provider":"local","code":"public class Main { public static void main(String[] args) { System.out.println(1); } }"}
```

## Important security note

**This is a local-development MVP, not a production code execution service.** The Java runner uses time limits and a heap cap, but it does not isolate filesystem, network, subprocesses, or OS permissions. Do not expose the backend to the public internet or run untrusted code. A container/VM sandbox with strict CPU, memory, filesystem, network, and process limits is required before public deployment.

## Current MVP limitations

- AI review and chat require Ollama or a configured remote provider.
- The editor is currently a single-file Java workspace; persistent projects, language-server autocomplete, and applied quick-fix workflows are future work.
- Run and review should be tested on your machine with the required JDK and AI provider installed.

## Tech stack
React · TypeScript · Vite · Monaco Editor · Java 21 · Spring Boot 3 · Ollama / OpenAI-compatible API
