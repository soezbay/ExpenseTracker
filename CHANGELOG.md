# Changelog – seit `84dbd34`

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

## Nicht-committed Änderungen (Working Tree)

- CSV-Pipeline komplett entfernt (8 Nodes → 3 Nodes)
- SQLite-Nodes entfernt
- JSONL append-only Persistenz
- Code-Node für Bild speichern
- Speicherpfad auf `/home/node/.n8n/expenseTracker/` geändert
- `/export` Kommando mit CSV-Export
