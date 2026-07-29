# Expense Tracker 🧾🤖

A Telegram bot that receives receipt photos, reads them locally via OCR
(Ollama), stores them in a structured format, and answers natural-language
questions about your expenses through an **AI Agent** (statistics,
period comparisons, top merchants, categories, single-receipt queries).

The complete n8n workflow is **programmatically** built and deployed via a
Kotlin client – no manual clicking in the n8n editor, everything is
versioned in Git.

- **Test the Bot:** https://t.me/YourLocalExpenseTrackerBot
- 📺 **Project Website:** https://soezbay.github.io/ExpenseTracker/
- 🎥 **Demo Video:** [youtu.be/QSYDz7L0088](https://youtu.be/QSYDz7L0088)
- 📜 **Change History:** [`CHANGELOG.md`](./CHANGELOG.md)

  [![Watch Demo Video](https://img.youtube.com/vi/QSYDz7L0088/0.jpg)](https://youtu.be/QSYDz7L0088)

---

## Features

- **Receipt OCR** – Telegram photo → Ollama Vision → structured JSON → JSONL persistence
- **AI Agent** – answers expense questions via tool calling:
  - `search_expenses` – search receipts
  - `get_receipt_by_id` – single receipt including all items
  - `get_summary_stats` – summary statistics
  - `compare_periods` – period comparisons (e.g. month over month)
  - `top_merchants` – top merchant ranking
  - `category_breakdown` – expenses by category (rule-based)
- **Excel Export** – `/export [year]` generates a `.xlsx` file
- **Manage Receipts** – `/list`, `/delete [year]`, `/help`
- **Local & Privacy-Friendly** – no cloud LLM, everything runs on self-hosted Ollama + n8n

## Architecture

```
Telegram Bot
    ↓
n8n Workflow (Trigger → OCR → Validation → Storage → AI Agent)
    ↓
Ollama (local LLM/Vision model)  →  OCR + AI Agent (tool calling)
    ↓
JSONL files (receipts_YYYY.jsonl) + images (bin/)
```

The entire workflow (all nodes, connections, prompts) is defined in
[`n8n-api-client/src/main/kotlin/TelegramReceiptWorkflow.kt`](./n8n-api-client/src/main/kotlin/TelegramReceiptWorkflow.kt)
and deployed via the n8n REST API (see
[`n8n-api-client/src/main/kotlin/N8nClient.kt`](./n8n-api-client/src/main/kotlin/N8nClient.kt)).

More technical details: [`AGENT.md`](./AGENT.md).

---

## Setup

### Requirements

- Docker & Docker Compose
- A Telegram bot token ([@BotFather](https://t.me/BotFather))
- JDK 17+ (only for running the Kotlin client locally)
- Optional: NVIDIA GPU + Container Toolkit (recommended for Ollama performance)

### 1. Start the Infrastructure (n8n + Ollama)

```bash
docker compose up -d
```

This starts:
- **n8n** at `http://localhost:5678`
- **Ollama** at `http://localhost:11434`

Pull models into Ollama (one-time):

```bash
docker exec -it ollama ollama pull qwen3-vl:4b
docker exec -it ollama ollama pull Keyvan/german-ocr-3.1:latest
docker exec -it ollama ollama pull Keyvan/german-text-3.1:latest
```

### 2. Create an n8n API Key

In n8n (`http://localhost:5678`): **Settings → API → Create API Key**.

### 3. Configure the Kotlin Client

```bash
cd n8n-api-client
cp .env.example .env
```

Fill in `.env`:

```env
N8N_URL=http://localhost:5678
N8N_API_KEY=<your-n8n-api-key>
TELEGRAM_BOT_TOKEN=<your-telegram-bot-token>
OLLAMA_URL=http://ollama:11434/api/generate
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_MODEL=qwen3-vl:4b
OLLAMA_OCR_MODEL=Keyvan/german-ocr-3.1:latest
OLLAMA_AGENT_MODEL=Keyvan/german-text-3.1:latest
```

> **Note:** `OLLAMA_URL`/`OLLAMA_BASE_URL` use the Docker service name
> `ollama` because n8n and Ollama run in the same Docker network (`expense-tracker-net`).

### 4. Deploy the Workflow

```bash
./gradlew runTelegram
```

This creates/updates the complete workflow in n8n including Telegram and
Ollama credentials (created automatically).

### 5. Test the Bot

In the Telegram chat with your bot:
- Send a receipt photo → processed automatically
- `How much did I spend this month?` → AI Agent replies
- `/export 2026`, `/list`, `/delete`, `/help`

For more details, all Kotlin client API endpoints and configuration options:
see [`n8n-api-client/README.md`](./n8n-api-client/README.md).

---

## Project Structure

```
ExpenseTracker/
├── docker-compose.yml              # n8n + Ollama stack
├── n8n-api-client/                 # Kotlin client, builds & deploys the workflow
│   └── src/main/kotlin/
│       ├── TelegramReceiptWorkflow.kt
│       ├── N8nClient.kt
│       └── Main.kt
├── docs/                           # GitHub Pages project website
├── Documents/                      # Sprint documentation, pitch deck
│   ├── Pitch/
│   ├── Sprint 1..4/
│   └── SprintSummaries.md
├── AGENT.md                        # Technical setup & status
└── CHANGELOG.md                    # Change history
```