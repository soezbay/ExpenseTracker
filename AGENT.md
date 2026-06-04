# ExpenseTracker - n8n Agent Setup

## Projekt-Übersicht

Automatisierte Kassenbon-Verarbeitung via Telegram und lokale OCR-Modell-Integration mit n8n.

**Stack:**
- **n8n** (Workflow-Automation) – läuft auf `https://n8n.sozbay.dev`
- **Telegram Bot** – empfängt Kassenbon-Fotos (auch als Media-Gruppe)
- **Ollama** – lokale Vision/OCR-Modelle (`deepseek-ocr`, `glm-ocr`, `Keyvan/german-ocr-3`)
- **Kotlin** – n8n API Client & Workflow-Builder

---

## Setup-Status

### ✅ Abgeschlossen
- [x] n8n API Client in Kotlin erstellt
- [x] Telegram Credential in n8n konfiguriert (auto-created via API)
- [x] Workflow-Builder für Kassenbon-Verarbeitung
- [x] OCR-Integration mit dynamischer Prompt-Selektion
- [x] JSON → lesbarer Text Formatierung via Code-Node
- [x] Media-Gruppen-Unterstützung (mehrere Bilder als Album)
- [x] n8n Attribution aus allen Telegram-Nachrichten entfernt
- [x] `.env` Datei mit Credentials (gitignore)

### ⏳ Ausstehend
- [ ] Ergebnisse in Datenbank speichern (z.B. Supabase/Postgres)
- [ ] Kategorisierung der Artikel hinzufügen
- [ ] Gruppenverarbeitung final testen (alle Bilder der Gruppe)
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
OLLAMA_MODEL=qwen3-vl:4b
OLLAMA_OCR_MODEL=Keyvan/german-ocr-3:latest
```

**Unterstützte OCR-Modelle:**
- `Keyvan/german-ocr-3` – **Empfohlen** für deutsche Rechnungen (strukturiertes JSON)
- `deepseek-ocr:latest` – Allgemeine OCR (Markdown/Plaintext)

---

## Workflow-Architektur

### Einzelbild-Flow

```
Telegram Trigger
    |
[Foto vorhanden?]
    | Ja
Antwort: "Validating photo.."
    |
Telegram getFile → Bild herunterladen → Zu Base64
    |
Ollama OCR (Validation + Extraction)
    |
[Ist Gruppe?] → Nein
    |
[Ist Kassenbon?]
    | Ja
Format OCR (JSON → lesbarer Text)
    |
Antwort: OCR Ergebnis
```

### Media-Gruppen-Flow

```
Ollama OCR
    |
[Ist Gruppe?] → Ja
    |
Gruppe: Speichern (Static Data)
    |
Gruppe: Warten (4s)
    |
Gruppe: Prüfen (alle Bilder zusammenführen)
    |
Antwort: Gruppen-Ergebnis
```

### Node-Details

| Node | Typ | Funktion |
|------|-----|----------|
| **Telegram Trigger** | Webhook | Empfängt Fotos & Media-Gruppen |
| **Foto vorhanden?** | IF | Prüft `message.photo` |
| **Telegram getFile** | HTTP | `getFile` API → `file_path` |
| **Bild herunterladen** | HTTP | Binary-Download via `file_path` |
| **Zu Base64** | Code | Binary → Base64 String |
| **Ollama OCR** | HTTP | POST an Ollama mit Bild + Prompt |
| **Ist Gruppe?** | IF | Prüft `media_group_id` |
| **Gruppe: Speichern** | Code | Static Data Zwischenspeicher |
| **Gruppe: Warten** | Wait | 4 Sekunden (alle Bilder ankommen) |
| **Gruppe: Prüfen** | Code | Ergebnisse zusammenführen & formatieren |
| **Ist Kassenbon?** | IF | Prüft ob OCR-Response nicht leer |
| **Format OCR** | Code | JSON → Telegram-lesbarer Text |
| **Antwort: OCR Ergebnis** | Telegram | Sendet formatierten Text |
| **Antwort: Gruppen-Ergebnis** | Telegram | Sendet gruppierte Ergebnisse |

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

### Telegram-Ausgabe

```
ALDI GmbH & Co. KG, Herten
Kurt-Schumacher-Str. 192, 45881 Gelsenkirchen
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
git commit -m "Update: OCR Integration mit Gruppen-Support"
git push origin main
```

---

## Nächste Schritte

1. **Ergebnisse speichern** → Datenbank-Node nach Format OCR einfügen
2. **Artikel-Kategorisierung** → Zweites LLM-Call für Kategorien
3. **Gruppenverarbeitung testen** → Mehrere Bilder als Album senden
4. **Git pushen** → Repository aktualisieren

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

**Zuletzt aktualisiert:** 2026-06-04 20:20 UTC+02:00
