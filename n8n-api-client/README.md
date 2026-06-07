# n8n API Client (Kotlin)

Ein Kotlin-Projekt zum Steuern von n8n über die REST API. Erstellt und deployed den **Expense Tracker** Workflow inkl. AI Agent.

## Features

- **Kassenbon-OCR** – Telegram-Fotos → Ollama Vision → strukturiertes JSON → JSONL-Persistenz
- **AI Agent** – Textfragen zu Ausgaben via `Keyvan/german-text-3.1:latest` (Ollama)
- **CSV-Export** – `/export [Jahr]` Kommando für Telegram
- **Auto-Credentials** – Telegram & Ollama Credentials werden automatisch in n8n erstellt

## API-Endpunkte

| Methode | Beschreibung |
|---------|-------------|
| `listWorkflows()` | Alle Workflows auflisten |
| `getWorkflow(id)` | Einzelnen Workflow abrufen |
| `createWorkflow(name)` | Workflow erstellen |
| `createFullWorkflow(request)` | Vollständigen Workflow mit Nodes erstellen |
| `updateWorkflow(id, workflow)` | Workflow aktualisieren |
| `deleteWorkflow(id)` | Workflow löschen |
| `activateWorkflow(id)` | Workflow aktivieren |
| `deactivateWorkflow(id)` | Workflow deaktivieren |
| `executeWorkflow(id)` | Workflow manuell ausführen |
| `findOrCreateTelegramCredential(botToken)` | Telegram-Credential erstellen/finden |
| `findOrCreateOllamaCredential(baseUrl)` | Ollama-Credential erstellen/finden |
| `listExecutions()` | Alle Executions auflisten |
| `getExecution(id)` | Einzelne Execution abrufen |

## Konfiguration

1. In n8n einen API-Key erstellen: **Settings > API**
2. `.env` Datei anlegen (siehe `.env.example`):

```env
N8N_URL=https://n8n.sozbay.dev
N8N_API_KEY=...
TELEGRAM_BOT_TOKEN=...
OLLAMA_URL=http://ollama:11434/api/generate
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_MODEL=qwen3-vl:4b
OLLAMA_OCR_MODEL=Keyvan/german-ocr-3:latest
OLLAMA_AGENT_MODEL=Keyvan/german-text-3.1:latest
```

## Ausführen

```bash
# Expense Tracker Workflow deployen
./gradlew runTelegram

# Allgemeine API-Demo
./gradlew run
```

Oder in IntelliJ IDEA: `TelegramReceiptWorkflow.kt` > Rechtsklick > **Run**.
