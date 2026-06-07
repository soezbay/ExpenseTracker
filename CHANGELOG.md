# Changelog 

## Zusammenfassung

Die Persistenz-Schicht wurde komplett neu implementiert. Der ursprüngliche CSV-basierte Ansatz (mit 8 Nodes: WriteBinaryFile, ExecuteCommand, ReadBinaryFile, ExtractFromFile, Code, ConvertToFile, WriteBinaryFile) wurde durch eine schlanke JSONL-Lösung mit nur 3 Nodes ersetzt. Zusätzlich wurden nicht verfügbare n8n-Nodes (`n8n-nodes-base.sqlite`) durch reine Code-Nodes ersetzt.

---

## Geänderte Dateien

- **TelegramReceiptWorkflow.kt** – Workflow-Persistenz komplett umgebaut
- **AGENT.md** – Dokumentation aktualisiert
- **.env.example** – OCR-Modell-Default angepasst

---

## Neuerungen im Detail

### 1. Persistenz: CSV → SQLite → JSONL

| Iteration | Ansatz | Problem |
|-----------|--------|---------|
| 1. CSV-Pipeline | 8 Nodes (Verzeichnis vorbereiten → CSV lesen → parsen → mergen → erstellen → speichern) | Komplex, viele Nodes, Race Conditions |
| 2. SQLite (`n8n-nodes-base.sqlite`) | Dedizierte SQLite-Nodes | Node nicht installiert im n8n-Image |
| 3. SQLite (`better-sqlite3` Code-Node) | Code-Node mit `require('better-sqlite3')` | Modul nicht im distroless n8n-Image; `bindings`-Fehler in Sandbox |
| 4. **JSONL (final)** | **1 Code-Node mit `fs.appendFileSync`** | **Keine Probleme – atomar, multi-user-sicher** |

### 2. Bild speichern: WriteBinaryFile → Code-Node

- `n8n-nodes-base.writeBinaryFile` scheiterte an n8n File-Access-Restrictions (`N8N_RESTRICT_FILE_ACCESS_TO`)
- Ersetzt durch Code-Node mit `fs.writeFileSync` + `fs.mkdirSync({ recursive: true })`
- Erstellt `bin/`-Verzeichnis automatisch

### 3. Workflow-Vereinfachung

**Vorher (committed):** 8 Persistenz-Nodes
```
Bild speichern → Verzeichnis vorbereiten → CSV lesen → CSV parsen
→ CSV zusammenfuehren → CSV erstellen → CSV speichern
+ Prep Persist
```

**Nachher (aktuell):** 3 Persistenz-Nodes
```
Restore Binary → Bild speichern (Code) → JSON speichern (Code)
```

### 4. Speicherort

- **Vorher:** `/media/raid_storage/n8n/expenseTracker/` (externer Mount nötig)
- **Nachher:** `/home/node/.n8n/expenseTracker/` (innerhalb des n8n_data Volumes)

### 5. Multi-User-Sicherheit

- `fs.appendFileSync` ist auf POSIX-Systemen atomar für kleine Writes
- Kein Read-Modify-Write-Zyklus → keine Race Conditions
- Jeder Beleg wird als einzelne JSON-Zeile in `receipts_YYYY.jsonl` angehängt

### 6. Datenformat (JSONL)

Jede Zeile in `receipts_2026.jsonl`:
```json
{"id":"chatId_msgId_random","chatId":123,"messageId":604,"createdAt":"2026-06-04T22:50:00Z","imagePath":"/home/node/.n8n/expenseTracker/bin/chatId_msgId_random.jpg","documentType":"Rechnung","invoiceNumber":"R-2026-001","invoiceDate":"04.06.2026","senderName":"Firma GmbH","amountTotal":119.00,"currency":"EUR","lineItems":[...]}
```

### 7. n8n Docker-Konfiguration

Benötigte Umgebungsvariablen im Stack:
```yaml
- NODE_FUNCTION_ALLOW_BUILTIN=fs,path
```

---

### 8. `/export` Kommando (CSV-Export per Telegram)

- User sendet `/export` oder `/export 2025` im Chat
- Code-Node liest `receipts_YYYY.jsonl`, filtert nach `chatId`
- Generiert CSV (Semikolon-getrennt, Excel-kompatibel)
- Sendet als Telegram-Dokument
- Bei 0 Belegen: CSV mit Hinweistext

---

## [2026-06-07] AI Agent & Format-Erweiterungen

### Geänderte Dateien

- **TelegramReceiptWorkflow.kt** – AI Agent Branch, Format OCR Erweiterungen
- **N8nClient.kt** – `findOrCreateOllamaCredential` hinzugefügt
- **AGENT.md** – Dokumentation aktualisiert
- **.env.example** – `OLLAMA_BASE_URL`, `OLLAMA_AGENT_MODEL` ergänzt

### Neuerungen im Detail

#### 1. AI Agent Integration

- Textnachrichten (kein Foto, kein `/export`) werden an einen n8n AI Agent weitergeleitet
- LLM: `Keyvan/german-text-3.1:latest` via Ollama Chat Model Sub-Node
- Tool-Calling: `search_expenses` durchsucht JSONL-Belege (aktuelle + Vorjahr) nach Suchbegriffen
- Window Buffer Memory: Konversations-Kontext (10 Nachrichten pro Chat-ID)
- Agent-Antwort wird als Telegram-Reply gesendet

#### 2. Reply-Kontext (Telegram Reply)

- Antwortet der User auf eine Nachricht (Telegram Reply), wird `message.reply_to_message.text` automatisch im Prompt mitgeliefert
- Prompt-Format:
  ```
  [Referenzierte Nachricht]:
  <Text der referenzierten Nachricht>

  [Meine Frage]:
  <Eigene Nachricht des Users>
  ```
- System-Prompt des Agents informiert ihn über dieses Format

#### 3. Format OCR – Steuernummer ergänzt

- `sender.vat_id` / `sender.vatid` wird jetzt in der Telegram-Ausgabe angezeigt
- Reihenfolge: Name → Adresse → **Steuernummer** → Datum → Beleg-Nr → Artikel → Gesamt → MwSt

#### 4. Neue Umgebungsvariablen

| Variable | Beschreibung |
|----------|-------------|
| `OLLAMA_BASE_URL` | Basis-URL für Ollama API (z.B. `http://ollama:11434`) |
| `OLLAMA_AGENT_MODEL` | LLM-Modell für den AI Agent |

## Nicht-committed Änderungen (Working Tree)

- CSV-Pipeline komplett entfernt (8 Nodes → 3 Nodes)
- SQLite-Nodes entfernt
- JSONL append-only Persistenz
- Code-Node für Bild speichern
- Speicherpfad auf `/home/node/.n8n/expenseTracker/` geändert
- `/export` Kommando mit CSV-Export
- AI Agent mit Ollama, Tool-Calling und Memory
- Reply-Kontext-Support im AI Agent
- Steuernummer in Format OCR Ausgabe
