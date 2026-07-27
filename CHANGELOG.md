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

## [2026-06-07] CSV Export Fixes, /list, /delete & Switch Node Refactoring

### Geänderte Dateien

- **TelegramReceiptWorkflow.kt** – CSV Export korrigiert, /list & /delete hinzugefügt, IF-Kette durch Switch Node ersetzt

### Neuerungen im Detail

#### 1. CSV Export – Fehlerbehebungen

- **Trennzeichen:** `;` → `,` (echtes CSV statt Semikolon-CSV)
- **Korrektes Quoting:** Felder mit `,`, `"` oder Zeilenumbrüchen werden in `"""` eingeschlossen (RFC 4180 kompatibel)
- **`amountTotal` Berechnung:** Wenn OCR `amountTotal` gleich `amountNet` liefert obwohl `amountVat > 0`, wird `amountNet + amountVat` automatisch berechnet
- **`senderName` Fallback:** Wenn OCR keinen Namen erkennt, wird die erste Zeile der `senderAddress` verwendet
- **Zahlenformatierung:** Alle Beträge werden mit 2 Dezimalstellen formatiert (`6.74` statt `6.740000000001`)

#### 2. Neue Telegram Kommandos

| Kommando | Beschreibung |
|----------|-------------|
| `/list` | Zeigt alle gespeicherten `receipts_YYYY.jsonl`-Dateien mit Beleg-Anzahl pro Jahr |
| `/delete <year>` | Löscht `receipts_<year>.jsonl` nach Bestätigung |
| `/delete all` | Löscht **alle** JSONL-Dateien nach Bestätigung |
| `/delete <year> confirm` | Bestätigt die Löschung |

**Beispiel:**
```
User: /delete 2026
Bot:  ⚠️ Bist du sicher? Diese Dateien werden gelöscht:
      receipts_2026.jsonl

      Sende /delete 2026 confirm um zu bestätigen.

User: /delete 2026 confirm
Bot:  ✅ Gelöscht:
      receipts_2026.jsonl
```

#### 3. Switch Node Refactoring

**Vorher (verkettete IF-Nodes):**
```
Foto vorhanden? → Export Kommando? → List Kommando? → Delete Kommando?
```

**Nachher (einzelner Switch Node):**
```
Nachricht Switch
  Output 0: Foto vorhanden   → Bildverarbeitung
  Output 1: /export           → CSV Export
  Output 2: /list            → Dateien auflisten
  Output 3: /delete          → Dateien löschen
  Output 4: default           → AI Agent
```

- **4 Nodes gespart:** `Foto vorhanden?`, `Export Kommando?`, `List Kommando?`, `Delete Kommando?` entfernt
- Klarere Routing-Logik: Ein Node statt verketteter IF-Branches
- Default-Output (Output 4): Alle unbekannten Textnachrichten → AI Agent

## [2026-07-09] Switch/IF Node Schema-Fixes, Kassenbon-Validierung, /help & Excel-Export

### Geänderte Dateien

- **TelegramReceiptWorkflow.kt** – Switch Node & IF Node auf aktuelles n8n-Schema migriert, Kassenbon-Validierung korrigiert, `/help` Kommando hinzugefügt, CSV-Export durch Excel-Export ersetzt

### Neuerungen im Detail

#### 1. Switch Node – Schema-Fix (`typeVersion` 2 → 3)

**Problem:** Nach dem Deploy blieben alle Nachrichten in der "Foto vorhanden"-Linie stecken; die Routing-Regeln waren im n8n UI leer.

**Ursache:** Das verwendete JSON-Schema (`rules.rules`, `output: 0`) entsprach dem alten Switch-Node-Format. Das aktuell installierte n8n (`2.20.7`) erwartet für Switch Nodes das v3-Schema:

| Alt (falsch) | Neu (korrekt) |
|---|---|
| `typeVersion: 2` | `typeVersion: 3` |
| `rules.rules: [...]` | `rules.values: [...]` |
| `output: 0` | `outputKey: "0"` |
| kein `combinator` | `combinator: "and"` pro Regel |
| kein Fallback | `options.fallbackOutput: "extra"` |

`fallbackOutput: "extra"` ist entscheidend: Ohne diese Option bricht der Switch Node bei nicht-matchenden Nachrichten mit "Workflow success" ab, statt zum Default-Output (AI Agent) zu routen.

#### 2. IF Node "Ist Kassenbon?" – Schema-Fix (`typeVersion` 1 → 2)

**Problem:** Kein einziger valider Kassenbon wurde als Kassenbon erkannt – alle Fotos landeten im "Kein Kassenbon"-Zweig.

**Ursache:** Der Node nutzte das Legacy-IF-Schema (`conditions.string[].value1/operation`), das vom aktuellen n8n nicht mehr korrekt eingelesen wird (`value1` wurde `undefined`, `isNotEmpty` immer `false`). Migriert auf das moderne Filter-Schema (`conditions.conditions[].leftValue/rightValue/operator`), analog zum Switch Node.

#### 3. Kassenbon-Validierung – Prompt-Fix

**Problem:** Auch für komplett fachfremde Bilder (z.B. ein Katzenfoto) gab das Vision-Modell eine nicht-leere JSON-Antwort zurück (Halluzination), wodurch die reine "nicht leer"-Prüfung fälschlich immer `true` ergab.

**Fix:**
- OCR-Prompt weist das Modell nun explizit an, zuerst zu prüfen ob überhaupt ein Kassenbon/Rechnung im Bild ist. Falls nicht: Antwort exakt `NOT_A_RECEIPT`.
- IF Node prüft zusätzlich per `notContains`, dass die Antwort **nicht** `NOT_A_RECEIPT` enthält (mit `AND` verknüpft zur bisherigen `notEmpty`-Prüfung).

#### 4. Neues Kommando: `/help`

Zeigt eine Übersicht aller verfügbaren Kommandos (Foto senden, `/export`, `/list`, `/delete`, freie Frage an den AI Agent). Integriert als Output 4 im `Nachricht Switch` Node (Output 5 ist jetzt der Default-Fallback zum AI Agent).

#### 5. Export-Format: CSV → Excel (.xlsx)

**Vorher:** Code-Node baute manuell einen CSV-String (Komma-getrennt, RFC 4180 Quoting) und schickte ihn als `.csv`-Binärdatei.

**Nachher:**
```
Beleg Zeilen (Code)  →  XLSX Export (Spreadsheet File)  →  Excel senden (Telegram)
```
- **Beleg Zeilen:** Liest JSONL, gibt ein Item pro Beleg zurück (echte Zahlen statt formatierter Strings)
- **XLSX Export:** Nativer `n8n-nodes-base.spreadsheetFile` Node konvertiert die Items zu einer `.xlsx`-Datei (Sheet "Belege") – kein externes npm-Package nötig
- **Excel senden:** Versendet die `.xlsx` per Telegram; Caption zeigt Beleg-Anzahl via `$('Beleg Zeilen').all().length`

## [2026-07-10] AI Agent Tools, Kategorisierung & Scanning-Fix

### Geänderte Dateien

- **TelegramReceiptWorkflow.kt** – neue Agent-Tools, Kategorisierung, Scanning-Fix

### Neuerungen im Detail

#### 1. Erweiterte Agent-Tools

- `get_summary_stats` – Summe, Durchschnitt, Anzahl, teuerster/günstigster Beleg für ein Jahr oder `YYYY-MM`
- `compare_periods` – Vergleich der Ausgaben zweier Jahre
- `top_merchants` – Top 5 Geschäfte nach Gesamtausgaben
- `get_receipt_by_id` – Details eines einzelnen Belegs inkl. Artikel, Steuernummer, IBAN, Notizen
- `search_expenses` liefert jetzt Beleg-IDs zurück und filtert nach `chatId`

#### 2. Agent-Antworten und Telegram Parsing

- Markdown-Zeichen (`**`, `*`, `_`, `` ` ``, `[`, `]`, `~`) werden vor dem Telegram-Versand entfernt
- `parseMode` auf `none` gesetzt → behebt `can't parse entities` 400-Fehler
- Antworten werden auf 4000 Zeichen gekürzt
- System-Prompt: maximal ein Tool pro Frage, genau eine finale Antwort, max. 500 Wörter, keine Wiederholungen

#### 3. Kategorisierung von Belegen

- Belege erhalten beim Speichern eine Kategorie per Keyword-Regeln (Groceries, Restaurant, Transport, Health, Electronics, Clothing, Leisure, Household, Other)
- Neues Agent-Tool `category_breakdown` schlüsselt Ausgaben nach Kategorie auf

#### 4. Scanning-Fix

- IF-Node "Is Receipt?" (vormals "Ist Kassenbon?") zurück auf `typeVersion: 1` gesetzt
- OCR-Prompt vereinfacht: direkte JSON-Extraktion ohne separaten `NOT_A_RECEIPT`-Check
- Übergangsweise hinzugefügte Ollama-Sampling-Optionen (`repeatPenalty`, `temperature`, `numPredict`) und `maxIterations` aus dem Agent entfernt

## [2026-07-26/27] Dokumentation, Refactoring, GitHub Pages & Demo Day

### Geänderte Dateien

- **TelegramReceiptWorkflow.kt** – auf modularen Builder reduziert
- **n8n-api-client/src/main/kotlin/nodes/\*.kt** – neue modulare Node-Dateien
- **README.md** – neues Root-README
- **docker-compose.yml** – Compose-Stack für n8n + Ollama
- **Documents/** – Sprint-Dokumente reorganisiert
- **docs/index.html**, **docs/index-en.html** – GitHub Pages
- **docs/ressources/\*.png** – neue Screenshots

### Neuerungen im Detail

#### 1. Refactoring der Kotlin-Workflow-Generierung

- `TelegramReceiptWorkflow.kt` wurde von ~1600 Zeilen auf den Builder-Orchstrator reduziert
- Node-Definitionen ausgelagert in:
  - `AgentNodes.kt` (AI Agent, LLM, Memory, Tools)
  - `CommandAndExportNodes.kt` (`/list`, `/delete`, `/help`, `/export`, Excel)
  - `CommonNodes.kt` (Trigger, Send Chat Action, Message Switch, Credentials)
  - `ErrorWorkflowNodes.kt` (Error Handler Workflow)
  - `ReceiptNodes.kt` (Foto-Download, OCR, Validierung, Persistenz)
  - `WorkflowConnections.kt` & `WorkflowIds.kt`
- UI-Texte, Node-Namen und OCR-Ausgabe ins Englische übersetzt

#### 2. Telegram "Typing"-Indikator

- Neuer `Send Chat Action` HTTP-Node ruft `https://api.telegram.org/bot<token>/sendChatAction` mit `action: 'typing'` direkt nach dem Trigger auf
- Message-Switch-Regeln referenzieren jetzt immer `$('Telegram Trigger').item.json.message` statt `$json.message`

#### 3. Dokumentation, Deployment & GitHub Pages

- Sprint-Dokumente in `Documents/Sprint 1`, `Documents/Sprint 2`, `Documents/Sprint 3`, `Documents/Sprint 4 Demo Day` umstrukturiert
- Demo-Day-Unterlagen (`DEMO_DAY_PRESENTATION.md`, `Expense Tracker Demo Day.mp4`, `Expense Tacker Demo Day Submission.pdf`) hinzugefügt
- Root `README.md` mit Architektur-Übersicht, Setup und Links hinzugefügt
- `docker-compose.yml` für n8n + Ollama hinzugefügt
- GitHub Pages `docs/index.html` überarbeitet
- Neue Screenshots `n8n-workflow-screenshot.png`, `ToolsUsageExample.png`, `AnyQueastionsExample.png`, `DifferentLanguagesExample.png` hinzugefügt
- Englische GitHub Pages Version `docs/index-en.html` hinzugefügt und verlinkt
- Screenshot-Pfade in der Pages-Seite korrigiert
