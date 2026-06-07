# ExpenseTracker - n8n Agent Setup

## Projekt-Übersicht

Automatisierte Kassenbon-Verarbeitung via Telegram und lokale OCR-Modell-Integration mit n8n.

**Stack:**
- **n8n** (Workflow-Automation) – läuft auf `https://n8n.sozbay.dev`
- **Telegram Bot** – empfängt Kassenbon-Fotos & Textfragen
- **Ollama** – lokale Vision/OCR-Modelle + AI Agent LLM
  - OCR: `Keyvan/german-ocr-3:latest`
  - Agent: `Keyvan/german-text-3.1:latest`
- **Kotlin** – n8n API Client & Workflow-Builder
- **n8n AI Agent** – LangChain-basierter Agent mit Tool-Calling & Memory

**Stack yaml:**
```version: '3.8'

services:
  coolercontrold:
    image: coolercontrol/coolercontrold:latest
    container_name: coolercontrold
    privileged: true
    restart: unless-stopped
    ports:
      - "11987:11987"
    volumes:
      - /etc/coolercontrol:/etc/coolercontrol
      
  n8n:
    # Use the newly built custom image
    image: n8n-custom:latest
    container_name: n8n
    restart: unless-stopped
    ports:
      - "8081:8081"  
    environment:
      - N8N_HOST=xxx
      - N8N_PORT=8081
      - N8N_PROTOCOL=http
      - GENERIC_TIMEZONE=Europe/Berlin
      - TZ=Europe/Berlin
      - WEBHOOK_URL=https://n8n.sozbay.dev/
      - NODE_FUNCTION_ALLOW_BUILTIN=fs,path
      - NODE_FUNCTION_ALLOW_EXTERNAL=better-sqlite3
      # Tell n8n where to find globally installed npm packages
      - NODE_PATH=/usr/local/lib/node_modules
    volumes:
      - n8n_data:/home/node/.n8n
      - /media/raid_storage/n8n/expenseTracker:/media/raid_storage/n8n/expenseTracker
    depends_on:
      - ollama
    networks:
      - proxy-net

  ollama:
    image: ollama/ollama:latest
    container_name: ollama
    restart: unless-stopped
    ports:
      - "8082:11434"
    volumes:
      - ollama_data:/root/.ollama
    networks:
      - proxy-net
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: [gpu]

  open-webui:
    image: ghcr.io/open-webui/open-webui:main
    container_name: open-webui
    restart: unless-stopped
    ports:
      - "3000:8080"
    environment:
      - OLLAMA_BASE_URL=http://ollama:11434
    volumes:
      - open-webui_data:/app/backend/data
    depends_on:
      - ollama
    networks:
      - proxy-net

networks:
  proxy-net:
    external: true

volumes:
  n8n_data:
  ollama_data:
  open-webui_data: 
  ```

---

## Setup-Status

### ✅ Abgeschlossen
- [x] n8n API Client in Kotlin erstellt
- [x] Telegram Credential in n8n konfiguriert (auto-created via API)
- [x] Ollama Credential in n8n konfiguriert (auto-created via API)
- [x] Workflow-Builder für Kassenbon-Verarbeitung
- [x] OCR-Integration mit dynamischer Prompt-Selektion
- [x] JSON → lesbarer Text Formatierung via Code-Node
- [x] n8n Attribution aus allen Telegram-Nachrichten entfernt
- [x] `.env` Datei mit Credentials (gitignore)
- [x] Bilder lokal mit eindeutigem Dateinamen speichern (`fs.writeFileSync`)
- [x] Persistenz via JSONL (append-only, multi-user-sicher)
- [x] `/export` Kommando – CSV-Export per Telegram Chat
- [x] AI Agent Integration (Ollama `Keyvan/german-text-3.1:latest`)
- [x] Tool-Calling: `search_expenses` – durchsucht JSONL-Belege
- [x] Window Buffer Memory – Konversations-Kontext per Chat-ID
- [x] Steuernummer (VAT ID) in Beleg-Ausgabe ergänzt
- [x] Reply-Kontext: Agent berücksichtigt referenzierte Nachrichten (Telegram Reply)
- [x] `/list` Kommando – Gespeicherte JSONL-Dateien auflisten
- [x] `/delete` Kommando – JSONL-Dateien löschen mit Bestätigung
- [x] CSV Export Fixes – Komma-Trennung, korrektes Quoting, amountTotal/senderName Berechnung
- [x] Switch Node Refactoring – Einzelner Node statt verketteter IF-Branches

### ⏳ Ausstehend
- [ ] Kategorisierung der Artikel hinzufügen
- [ ] Weitere Agent-Tools (z.B. Ausgaben-Statistiken, Monatsvergleich)
- [ ] Git Repository pushen

---

## Konfiguration

### 1. .env Datei

**Datei:** `n8n-api-client/.env`

```env
N8N_URL=https://n8n.sozbay.dev
N8N_API_KEY=dein_n8n_api_key_hier
TELEGRAM_BOT_TOKEN=dein_telegram_bot_token_hier

# Ollama (fuer Docker-Stack: Service-Namen verwenden)
OLLAMA_URL=http://ollama:11434/api/generate
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_MODEL=qwen3-vl:4b
OLLAMA_OCR_MODEL=Keyvan/german-ocr-3:latest

# AI Agent Model (Chat/Text Model für Ausgaben-Assistent)
OLLAMA_AGENT_MODEL=Keyvan/german-text-3.1:latest
```

**Unterstützte OCR-Modelle:**
- `Keyvan/german-ocr-3` – **Empfohlen** für deutsche Rechnungen (strukturiertes JSON)
- `deepseek-ocr:latest` – Allgemeine OCR (Markdown/Plaintext)

**Agent-Modell:**
- `Keyvan/german-text-3.1:latest` – Deutsches Text-Modell mit Tool-Calling-Support

---

## Workflow-Architektur

### Hauptflow (Bild empfangen)

```
Telegram Trigger
    |
[Nachricht Switch]
    | Foto    | /export   | /list    | /delete   | default
    |         |           |          |           |
    |    CSV Export  Dateien      Dateien    AI Agent
    |         |      auflisten    loeschen       |
    |    CSV senden     |            |      Antwort: Agent
    |                   |            |
Antwort: "Validating photo.."
    |
Telegram getFile → Bild herunterladen → Zu Base64
    |
Ollama OCR (Validation + Extraction)
    |
[Ist Kassenbon?]
    | Ja                                      | Nein
    |                                    Antwort: Kein Kassenbon
Format OCR + Restore Binary (parallel)
    |              |
Antwort: OCR   Bild speichern → JSON speichern
```

### AI Agent Flow

User sendet eine Textnachricht (kein Foto, kein `/export`) → AI Agent antwortet.

```
Export Kommando? → false
    |
  AI Agent (Keyvan/german-text-3.1:latest)
    ├── Ollama Chat Model (LLM)
    ├── search_expenses Tool (JSONL-Suche)
    └── Window Buffer Memory (per chatId, 10 Nachrichten)
    |
  Antwort: Agent → Telegram sendMessage
```

**Fähigkeiten:**
- Belege nach Geschäft, Datum, Betrag oder Artikeln durchsuchen
- Zusammenfassungen erstellen (Gesamtausgaben, Top-Geschäfte)
- Fragen zu gespeicherten Ausgaben beantworten
- Konversations-Kontext über mehrere Nachrichten merken
- **Reply-Kontext:** Antwortet der User auf eine Bot-Nachricht, wird der referenzierte Text als `[Referenzierte Nachricht]` automatisch mitgeliefert

### Export-Flow

User sendet `/export` oder `/export 2025` → Bot antwortet mit CSV-Datei.
- Liest `receipts_YYYY.jsonl`
- Filtert nach `chatId` des Users
- Generiert echtes CSV (Komma-getrennt, RFC 4180 Quoting)
- Korrigiert `amountTotal` wenn OCR `amountNet + amountVat` ignoriert hat
- Sendet als Telegram-Dokument

### List-Flow

User sendet `/list` → Bot antwortet mit Liste aller gespeicherten `receipts_YYYY.jsonl`-Dateien und deren Beleg-Anzahl.

### Delete-Flow

User sendet `/delete 2026` → Bot zeigt Vorschau und verlangt Bestätigung.
User sendet `/delete 2026 confirm` → Datei wird gelöscht.
User sendet `/delete all` oder `/delete all confirm` für alle Dateien.

### Node-Details

| Node | Typ | Funktion |
|------|-----|----------|
| **Telegram Trigger** | Webhook | Empfängt Fotos und Textkommandos |
| **Nachricht Switch** | Switch | Routet Nachrichten: Foto → Bildverarbeitung, /export → CSV, /list → Liste, /delete → Löschen, Default → Agent |
| **CSV Export** | Code | Liest JSONL, filtert nach User, generiert CSV (Komma, korrektes Quoting) |
| **CSV senden** | Telegram | Sendet CSV als Dokument |
| **Dateien auflisten** | Code | Listet alle `receipts_YYYY.jsonl` mit Beleg-Anzahl |
| **Antwort: Liste** | Telegram | Sendet Listen-Ergebnis |
| **Dateien loeschen** | Code | Löscht JSONL-Dateien (mit `confirm` Schutz) |
| **Antwort: Geloescht** | Telegram | Sendet Lösch-Ergebnis |
| **AI Agent** | LangChain Agent | Beantwortet Fragen zu Ausgaben via Ollama |
| **Ollama Chat Model** | LLM Sub-Node | `Keyvan/german-text-3.1:latest` |
| **search_expenses** | Tool Code | Durchsucht JSONL-Belege nach Suchbegriffen |
| **Window Buffer Memory** | Memory Sub-Node | Speichert Konversations-Kontext (10 Nachrichten pro Chat) |
| **Antwort: Agent** | Telegram | Sendet Agent-Antwort als Reply |
| **Telegram getFile** | HTTP | `getFile` API → `file_path` |
| **Bild herunterladen** | HTTP | Binary-Download via `file_path` |
| **Zu Base64** | Code | Binary → Base64 + `chatId`, `messageId`, `receiptId` |
| **Ollama OCR** | HTTP | POST an Ollama mit Bild + Prompt |
| **Ist Kassenbon?** | IF | Prüft ob OCR-Response nicht leer |
| **Format OCR** | Code | JSON → Telegram-lesbarer Text |
| **Antwort: OCR Ergebnis** | Telegram | Sendet formatierten Text |
| **Restore Binary** | Code | Holt Binary vom Download-Node zurück |
| **Bild speichern** | Code | `fs.writeFileSync` – speichert Bild als `<receiptId>.jpg/.png` |
| **JSON speichern** | Code | `fs.appendFileSync` – anhängen an `receipts_YYYY.jsonl`. Korrigiert amountTotal/senderName |

---

## OCR Prompts (dynamisch)

Die Prompts werden automatisch basierend auf `OLLAMA_OCR_MODEL` gewählt:

| Modell-Prefix | Prompt |
|---------------|--------|
| `Keyvan/german-ocr` / `german-ocr` | `Extrahiere die Rechnung im Bild als JSON.` |
| `deepseek-ocr` | `Extract the text in the image.` |
| *(default)* | `Extract the text in the image.` |

---

## Verwendung

### Workflow erstellen & deployen

```bash
cd n8n-api-client
.\gradlew runTelegram
```

**Output:**
```
✅ Credential erstellt: <credential-id>
✅ Ollama Credential: <ollama-credential-id>
✅ Alten Workflow geloescht.
✅ Workflow erstellt: [<workflow-id>] Expense Tracker
```

### Manuell testen

1. Öffne `https://n8n.sozbay.dev`
2. Gehe zu **Workflows**
3. Öffne **"Expense Tracker"**
4. Aktiviere den Workflow (Toggle oben rechts)
5. Sende ein Foto an deinen Telegram Bot

---

## OCR-Integration

### Ollama API Request

```json
{
  "model": "Keyvan/german-ocr-3",
  "prompt": "Extrahiere die Rechnung im Bild als JSON.",
  "stream": false,
  "images": ["base64_encoded_image"]
}
```

### Beispiel-Antwort (german-ocr-3)

```json
{
  "document_type": "receipt",
  "language": "de",
  "invoice_number": "0064 102 715611 0496",
  "invoice_date": "31/01/2026",
  "sender": {
    "name": "ALDI GmbH & Co. KG",
    "address": "Kurt-Schumacher-Str. 192, 45881 Gelsenkirchen",
    "vat_id": "DE127135535"
  },
  "line_items": [...],
  "amount_total": 21.17,
  "currency": "EUR"
}
```

### JSONL Persistenz

**Datenpfad:** `/home/node/.n8n/expenseTracker/`

**Dateien:**
- `receipts_YYYY.jsonl` – Eine JSON-Zeile pro Beleg (append-only, multi-user-sicher)
- `bin/<receiptId>.jpg/.png` – Original-Bilder

**Felder pro Beleg:**
`id`, `chatId`, `messageId`, `createdAt`, `imagePath`, `documentType`, `language`, `invoiceNumber`, `invoiceDate`, `dueDate`, `senderName`, `senderAddress`, `senderVatId`, `senderIban`, `amountNet`, `amountVat`, `amountTotal`, `currency`, `notes`, `lineItems[]`

**receiptId:** `<chatId>_<messageId>_<random>` (global eindeutig)

**Docker-Umgebungsvariablen (Portainer Stack):**
```yaml
- NODE_FUNCTION_ALLOW_BUILTIN=fs,path
```

### Telegram-Ausgabe

```
ALDI GmbH & Co. KG, Herten
Kurt-Schumacher-Str. 192, 45881 Gelsenkirchen
Steuernummer: DE127135535
Datum: 31/01/2026
Beleg-Nr: 0064 102 715611 0496

--- Artikel ---
5x ERDNUSSKERNE 4.95 EUR
3x PAPRIKA KARTOFFELCHIPS 3.33 EUR
...

Gesamt: 21.17 EUR
MwSt: 1.38 EUR
```

---

## Troubleshooting

### Problem: "Bad Request: message text is empty"
**Lösung:** `appendAttribution` in allen Telegram-Nodes auf `false` setzen. Bereits im Workflow implementiert.

### Problem: "OCR antwortet nicht"
**Lösung:**
- Ollama läuft? `ollama list` → Modell vorhanden?
- GPU läuft? `nvidia-smi`
- Timeout zu niedrig? Default ist 300s.

### Problem: "Bild wird nicht erkannt"
**Lösung:**
- Telegram muss das Bild als `message.photo` senden (kein Document)
- Vision/OCR-Modell verwenden (nicht Text-Only)

---

## Git Push

```powershell
cd C:\Users\safoz\IdeaProjects\ExpenseTracker
git add .
git commit -m "JSONL Persistenz + /export CSV-Export"
git push origin main
```

---

## Nächste Schritte

1. **Artikel-Kategorisierung** → Zweites LLM-Call für Kategorien
2. **Weitere Agent-Tools** → Monatsvergleich, Top-Ausgaben, Budgetwarnungen
3. **Git pushen** → Repository aktualisieren
4. **Dashboard** → Web-UI für Auswertungen

---

## Dateien

```
ExpenseTracker/
├── n8n-api-client/
│   ├── src/main/kotlin/
│   │   ├── Main.kt                    # Entry point (runTelegram)
│   │   ├── TelegramReceiptWorkflow.kt # Workflow-Builder
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

**Zuletzt aktualisiert:** 2026-06-07 21:30 UTC+02:00
