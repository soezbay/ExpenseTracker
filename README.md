# Expense Tracker 🧾🤖

Ein Telegram-Bot, der Kassenbons per Foto entgegennimmt, sie lokal per OCR
(Ollama) ausliest, strukturiert speichert und über einen **AI Agent**
natürliche Fragen zu deinen Ausgaben beantwortet (Statistiken,
Zeitraumvergleiche, Top-Händler, Kategorien, Einzelbeleg-Abfrage).

Der komplette n8n-Workflow wird **programmatisch** über einen Kotlin-Client
gebaut und deployed – kein manuelles Klicken im n8n-Editor, alles
versioniert in Git.

- 📺 **Projekt-Website / Live-Demo:** _TODO: GitHub Pages Link einfügen, siehe [`docs/`](./docs)_
- 🎥 **Demo-Video:**

  <video src="./docs/ressources/videos/ExpenseTrackerDemo.mp4" controls width="100%"></video>
- 📊 **Pitch Deck:** [`Documents/Pitch/Smart Expense Tracker - Safak Özbay.pdf`](./Documents/Pitch/Smart%20Expense%20Tracker%20-%20Safak%20Özbay.pdf)
- 🗒️ **Demo Day Präsentation (Sprechnotizen):** [`Documents/Sprint 4 Demo Day/DEMO_DAY_PRESENTATION.md`](./Documents/Sprint%204%20Demo%20Day/DEMO_DAY_PRESENTATION.md)
- 📜 **Change-History:** [`CHANGELOG.md`](./CHANGELOG.md) · [`Documents/SprintSummaries.md`](./Documents/SprintSummaries.md) · vollständige Historie via `git log`

---

## Features

- **Kassenbon-OCR** – Telegram-Foto → Ollama Vision → strukturiertes JSON → JSONL-Persistenz
- **AI Agent** – beantwortet Fragen zu Ausgaben per Tool-Calling:
  - `search_expenses` – Belege durchsuchen
  - `get_receipt_by_id` – Einzelbeleg inkl. aller Artikel
  - `get_summary_stats` – Zusammenfassungs-Statistiken
  - `compare_periods` – Zeitraumvergleiche (z.B. Monat zu Monat)
  - `top_merchants` – Top-Händler-Ranking
  - `category_breakdown` – Ausgaben nach Kategorie (regelbasiert)
- **Excel-Export** – `/export [Jahr]` erzeugt eine `.xlsx`-Datei
- **Belege verwalten** – `/list`, `/delete [Jahr]`, `/help`
- **Lokal & datenschutzfreundlich** – kein Cloud-LLM, alles läuft über selbst-gehostetes Ollama + n8n

## Architektur

```
Telegram Bot
    ↓
n8n Workflow (Trigger → OCR → Validierung → Speichern → AI Agent)
    ↓
Ollama (lokales LLM/Vision-Modell)  →  OCR + AI Agent (Tool-Calling)
    ↓
JSONL-Dateien (receipts_YYYY.jsonl) + Bilder (bin/)
```

Der gesamte Workflow (alle Nodes, Connections, Prompts) wird in
[`n8n-api-client/src/main/kotlin/TelegramReceiptWorkflow.kt`](./n8n-api-client/src/main/kotlin/TelegramReceiptWorkflow.kt)
definiert und per n8n REST API deployed (siehe
[`n8n-api-client/src/main/kotlin/N8nClient.kt`](./n8n-api-client/src/main/kotlin/N8nClient.kt)).

Weitere technische Details: [`AGENT.md`](./AGENT.md).

---

## Setup

### Voraussetzungen

- Docker & Docker Compose
- Ein Telegram-Bot-Token ([@BotFather](https://t.me/BotFather))
- JDK 17+ (nur für das lokale Ausführen des Kotlin-Clients)
- Optional: NVIDIA GPU + Container Toolkit (empfohlen für Ollama-Performance)

### 1. Infrastruktur starten (n8n + Ollama)

```bash
docker compose up -d
```

Das startet:
- **n8n** auf `http://localhost:5678`
- **Ollama** auf `http://localhost:11434`

Modelle in Ollama laden (einmalig):

```bash
docker exec -it ollama ollama pull qwen3-vl:4b
docker exec -it ollama ollama pull Keyvan/german-ocr-3.1:latest
docker exec -it ollama ollama pull Keyvan/german-text-3.1:latest
```

### 2. n8n API-Key erstellen

In n8n (`http://localhost:5678`): **Settings → API → Create API Key**.

### 3. Kotlin-Client konfigurieren

```bash
cd n8n-api-client
cp .env.example .env
```

`.env` befüllen:

```env
N8N_URL=http://localhost:5678
N8N_API_KEY=<dein-n8n-api-key>
TELEGRAM_BOT_TOKEN=<dein-telegram-bot-token>
OLLAMA_URL=http://ollama:11434/api/generate
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_MODEL=qwen3-vl:4b
OLLAMA_OCR_MODEL=Keyvan/german-ocr-3.1:latest
OLLAMA_AGENT_MODEL=Keyvan/german-text-3.1:latest
```

> **Hinweis:** `OLLAMA_URL`/`OLLAMA_BASE_URL` verwenden den Docker-Service-Namen
> `ollama`, da n8n und Ollama im selben Docker-Netzwerk (`expense-tracker-net`)
> laufen.

### 4. Workflow deployen

```bash
./gradlew runTelegram
```

Das erstellt/aktualisiert den kompletten Workflow in n8n inkl. Telegram- und
Ollama-Credentials (werden automatisch angelegt).

### 5. Bot testen

Im Telegram-Chat mit deinem Bot:
- Ein Kassenbon-Foto senden → wird automatisch verarbeitet
- `Wie viel habe ich diesen Monat ausgegeben?` → AI Agent antwortet
- `/export 2026`, `/list`, `/delete`, `/help`

Weitere Details, alle API-Endpunkte des Kotlin-Clients und Konfigurationsoptionen:
siehe [`n8n-api-client/README.md`](./n8n-api-client/README.md).

---

## Projektstruktur

```
ExpenseTracker/
├── docker-compose.yml              # n8n + Ollama Stack
├── n8n-api-client/                 # Kotlin-Client, baut & deployed den Workflow
│   └── src/main/kotlin/
│       ├── TelegramReceiptWorkflow.kt
│       ├── N8nClient.kt
│       └── Main.kt
├── docs/                           # GitHub Pages Projekt-Website
├── Documents/                      # Sprint-Dokumentation, Pitch Deck
│   ├── Pitch/
│   ├── Sprint 1..4/
│   └── SprintSummaries.md
├── AGENT.md                        # Technisches Setup & Status
└── CHANGELOG.md                    # Änderungshistorie
```

## Change History

Vollständige Commit-Historie: `git log` bzw. auf GitHub unter
[Commits](https://github.com/soezbay/ExpenseTracker/commits/main).
Zusammenfassungen pro Sprint: [`Documents/SprintSummaries.md`](./Documents/SprintSummaries.md),
detaillierte technische Änderungen: [`CHANGELOG.md`](./CHANGELOG.md).
