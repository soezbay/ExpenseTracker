# ExpenseTracker - n8n Agent Setup

## Projekt-Übersicht

Automatisierte Kassenbon-Verarbeitung via Telegram und lokale LLM-Integration mit n8n.

**Stack:**
- **n8n** (Workflow-Automation) – läuft auf `https://n8n.sozbay.dev`
- **Telegram Bot** – empfängt Kassenbon-Fotos
- **Lokale LLM** (z.B. Ollama) – analysiert Bilder auf dem Homeserver
- **Kotlin** – n8n API Client

---

## Setup-Status

### ✅ Abgeschlossen
- [x] n8n API Client in Kotlin erstellt
- [x] Telegram Credential in n8n konfiguriert (ID: `5HZvjIhAH6nQ8`)
- [x] Workflow-Builder für Kassenbon-Verarbeitung
- [x] HTTP Request Node für LLM-Integration
- [x] `.env` Datei mit Credentials (gitignore)

### ⏳ Ausstehend
- [ ] LLM-API-URL in `TelegramReceiptWorkflow.kt:28` eintragen
- [ ] Workflow via `.\gradlew runTelegram` erstellen
- [ ] Workflow in n8n testen (Foto an Bot senden)
- [ ] Kassenbon-Analyse-Prompt optimieren (`.kt:140`)
- [ ] Ergebnisse speichern (Datenbank/API)
- [ ] Git Repository pushen

---

## Konfiguration

### 1. LLM-API-URL eintragen

**Datei:** `n8n-api-client/src/main/kotlin/TelegramReceiptWorkflow.kt:28`

```kotlin
val llmApiUrl = "http://192.168.1.100:11434/api/generate"  // Deine LLM-URL
```

**Beispiele:**
- **Ollama:** `http://localhost:11434/api/generate`
- **llama.cpp:** `http://localhost:8000/completion`
- **vLLM:** `http://localhost:8000/v1/chat/completions`

### 2. .env Datei

**Datei:** `n8n-api-client/.env`

```env
N8N_URL=https://n8n.sozbay.dev
N8N_API_KEY=dein_api_key_hier
TELEGRAM_BOT_TOKEN=dein_bot_token_hier
```

---

## Workflow-Architektur

```
Telegram Trigger
    ↓
[Foto vorhanden?] (IF-Node)
    ↓ (ja)
An LLM senden (HTTP POST)
    ↓
Kassenbon-Daten zurück
```

### Node-Details

| Node | Typ | Funktion |
|------|-----|----------|
| **Telegram Trigger** | Webhook | Wartet auf Fotos |
| **Foto vorhanden?** | IF | Prüft `message.photo` |
| **An LLM senden** | HTTP Request | POST an lokale LLM |

---

## Verwendung

### Workflow erstellen & aktivieren

```bash
cd n8n-api-client
.\gradlew runTelegram
```

**Output:**
```
✅ Workflow erstellt: [workflow-id] Kassenbon an LLM
✅ Workflow aktiv: true
🤖 Workflow aktiv!
Das Bild wird an deine lokale LLM gesendet: http://...
```

### Manuell testen

1. Öffne `https://n8n.sozbay.dev`
2. Gehe zu **Workflows**
3. Öffne **"Kassenbon an LLM"**
4. Klicke **[Test]** und sende ein Foto an deinen Telegram Bot

---

## LLM-Integration

### Ollama (Standard)

```json
{
  "model": "llava",
  "prompt": "Analysiere diesen Kassenbon. Liste alle Artikel, Preise und das Datum auf.",
  "stream": false,
  "images": ["base64_encoded_image"]
}
```

### Andere LLMs

Falls deine LLM ein anderes Format erwartet, passe den Body in `TelegramReceiptWorkflow.kt:138-145` an.

---

## Troubleshooting

### Problem: "Credential nicht gefunden"
**Lösung:** Credential-ID in n8n überprüfen:
- Settings → Credentials → Telegram → ID kopieren
- In `TelegramReceiptWorkflow.kt:27` eintragen

### Problem: "LLM antwortet nicht"
**Lösung:** 
- LLM-URL testen: `curl http://your-llm:port/api/generate`
- Firewall-Regeln prüfen
- Ollama läuft? `ollama serve`

### Problem: "Bild wird nicht erkannt"
**Lösung:**
- Telegram muss das Bild als `message.photo` senden (kein Document)
- LLM muss Vision-Modell sein (z.B. `llava`, nicht `llama2`)

---

## Git Push

```powershell
cd C:\Users\safaoezb\IdeaProjects\ExpenseTracker
git init
git add .
git commit -m "Initial commit: n8n Kassenbon-Verarbeitung"
git remote add origin https://github.com/soezbay/ExpenseTracker.git
git branch -M main
git push -u origin main
```

---

## Nächste Schritte

1. **LLM-URL eintragen** → `TelegramReceiptWorkflow.kt:28`
2. **Workflow testen** → `.\gradlew runTelegram`
3. **Kassenbon-Prompt anpassen** → `TelegramReceiptWorkflow.kt:140`
4. **Ergebnisse speichern** → Datenbank-Integration hinzufügen
5. **Git pushen** → Repository aktualisieren

---

## Dateien

```
ExpenseTracker/
├── n8n-api-client/
│   ├── src/main/kotlin/
│   │   ├── Main.kt                    # Basis-Demo
│   │   ├── TelegramReceiptWorkflow.kt # Kassenbon-Workflow
│   │   ├── N8nClient.kt               # n8n API Wrapper
│   │   └── Models.kt                  # Datenklassen
│   ├── build.gradle.kts
│   ├── .env                           # Credentials (gitignore)
│   ├── .env.example                   # Template
│   └── README.md
├── AGENT.md                           # Diese Datei
└── ...
```

---

**Zuletzt aktualisiert:** 2026-05-16 14:47 UTC+02:00
