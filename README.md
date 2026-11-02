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

## Verify both AI providers

The backend reads configuration from **environment variables**. The file `backend/.env.example` is a reference template; Spring Boot does not automatically load that file. Keep real credentials in your local terminal environment and never commit them.

### Option A — Local Ollama

1. Install and start Ollama from [ollama.com](https://ollama.com).
2. In a terminal, download the default model:

   ```powershell
   ollama pull qwen2.5-coder:7b
   ollama list
   ```

3. In the PowerShell terminal where you will start the backend, set:

   ```powershell
   $env:OLLAMA_URL = "http://localhost:11434"
   $env:OLLAMA_MODEL = "qwen2.5-coder:7b"
   cd backend
   mvn spring-boot:run
   ```

4. In another terminal, test the connection:

   ```powershell
   Invoke-RestMethod "http://localhost:8080/api/ai/test?provider=local"
   ```

If this fails, check that Ollama is running, the model appears in `ollama list`, and `OLLAMA_URL` points to the Ollama server.

### Option B — Remote OpenAI-compatible API

Use an API key from your chosen provider. Do not paste it into the frontend or commit it to GitHub. Start the backend from a PowerShell terminal with:

```powershell
$env:OPENAI_API_KEY = "YOUR_API_KEY"
$env:OPENAI_MODEL = "gpt-4o-mini"
$env:OPENAI_BASE_URL = "https://api.openai.com/v1"
cd backend
mvn spring-boot:run
```

Replace the model and base URL with the values for your provider if using another OpenAI-compatible service. For the standard OpenAI endpoint, the base URL should end at `/v1`; the backend appends `/chat/completions`.

Test it in a second terminal:

```powershell
Invoke-RestMethod "http://localhost:8080/api/ai/test?provider=remote"
```

If you see an authentication or model error, confirm that the key is active, the account has API access/billing configured as required, and the model is available to that key. Restart Spring Boot after changing environment variables.

### Test the frontend

With the backend running, open a second PowerShell terminal at the repository root:

```powershell
npm install
npm run build
npm run dev
```

Open the Vite URL printed by the terminal (normally `http://localhost:5173`). The app should show the backend connection status. Select **Local · Ollama** or **Remote API**, edit the Java code, then try:

- **Run code** — compiles/runs the current Java source; does not require an AI provider.
- **Review** — asks the selected provider for a review of the current file.
- **Chat about your code** — sends your question and the current editor contents to the selected provider.

The frontend proxies `/api` requests to `http://localhost:8080` during Vite development. The API key remains on the backend and is never sent to the browser.

### API endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/health` | Backend health/version |
| POST | `/api/run` | Compile and run Java source |
| POST | `/api/review` | Review code using selected AI provider |
| POST | `/api/chat` | Ask a question about current code |
| GET | `/api/ai/providers` | Provider configuration status (not a live reachability test) |
| GET | `/api/ai/test?provider=local` | Send a test prompt to Ollama |
| GET | `/api/ai/test?provider=remote` | Send a test prompt to remote AI |

Example review request:

```powershell
$body = @{
  fileName = "Main.java"
  provider = "local"
  code = 'public class Main { public static void main(String[] args) { System.out.println(1); } }'
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/review" -ContentType "application/json" -Body $body
```

Example chat request:

```powershell
$body = @{
  fileName = "Main.java"
  provider = "local"
  code = 'public class Main { public static void main(String[] args) { System.out.println(1); } }'
  message = "Explain this code for a beginner."
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/chat" -ContentType "application/json" -Body $body
```

## Important security note

**This is a local-development MVP, not a production code execution service.** The Java runner uses time limits and a heap cap, but it does not isolate filesystem, network, subprocesses, or OS permissions. Do not expose the backend to the public internet or run untrusted code. A container/VM sandbox with strict CPU, memory, filesystem, network, and process limits is required before public deployment.

## Current MVP limitations

- AI review and chat require Ollama or a configured remote provider.
- The editor is currently a single-file Java workspace; persistent projects, language-server autocomplete, and applied quick-fix workflows are future work.
- Run and review should be tested on your machine with the required JDK and AI provider installed.

## Tech stack
React · TypeScript · Vite · Monaco Editor · Java 21 · Spring Boot 3 · Ollama / OpenAI-compatible API
