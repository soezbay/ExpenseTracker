# Sprint Reviews

Sprint presentation content (10 min, live demo encouraged):

Sprint goal recap — What did you set out to build?
Demo / progress — Show what works. Live demos strongly preferred over slides.
Technical decisions — What did you choose and why? What trade-offs did you make?
Learnings — What surprised you? What was harder/easier than expected?
Next sprint plan — What will you tackle next?
Open questions — Where do you need help or feedback?

## Sprint 1

**Ziel:** Automatisch deployten n8n-Workflow bauen, der Telegram-Fotos per Ollama als Kassenbon klassifiziert und dem Nutzer antwortet.

**Was gebaut wurde:**
- Kotlin-API-Client (Ktor) der den kompletten n8n-Workflow programmatisch erstellt und deployed auf eigenem HomeServer
- Telegram-Integration: Foto-Empfang, Zwischenantwort „Validating photo..", Reply auf Original-Nachricht
- Bild-Download via Telegram API + base64-Konvertierung für Ollama
- Ollama-Anbindung (lokales Vision-Modell, Gemma4)
- Timeout-Handling mit dedizierter Fehler-Node
- Automatische Credential-Wiederverwendung (findOrCreateTelegramCredential)

**Neu seit Sprint 0:** Alles

**Offenes Problem:** HomeServer GPU wird aktuell beim Berechnen von Bildern zu heiß durch fehlender aktiver Kühlung,
wodurch Anfragen limitiert werden müssen.

---

## Sprint 2

**Ziel:** OCR-Extraktion von Kassenbons in strukturierte Daten, persistente Speicherung und Export-Funktion.

**Was gebaut wurde:**
- Direkt Vision OCR als validierungsschritt sowie extraktionsschritt implementiert 
- OCR-Modell gewechselt: `deepseek-ocr` → `Keyvan/german-ocr-3` (strukturiertes JSON speziallisiert auf Rechnungen, basiert auf qwen3.5 8b)
- Format-Node: parst OCR-JSON und formatiert es als lesbaren Telegram-Text
- Bild-Persistenz: Code-Node mit `fs.writeFileSync` speichert Originalbilder unter `/home/node/.n8n/expenseTracker/bin/`
- Daten-Persistenz: JSONL-Format mit `fs.appendFileSync` (atomar, multi-user-sicher) → `receipts_YYYY.jsonl`
- `/export`-Kommando: User sendet `/export` oder `/export 2025` → Bot generiert CSV und sendet sie als Telegram-Dokument
- CSV ist nach `chatId` gefiltert (User-Isolation) und Semikolon-getrennt (Excel-kompatibel)

**Neu seit Sprint 1:**
- Strukturierte Datenextraktion statt nur Bild-Klassifikation
- Persistenz (vorher: nichts gespeichert)
- Textkommandos (`/export`) neben Foto-Verarbeitung

**Technische Entscheidungen:**
- JSONL statt SQLite und CSV → n8n Sandbox blockiert native Module (`better-sqlite3` Binding-Fehler)
- `fs.appendFileSync` statt Read-Modify-Write → keine Race Conditions bei parallelen Requests
- Code-Nodes statt WriteBinaryFile → n8n File-Access-Restrictions umgangen

**Offene Punkte:**
- CSV Struktur ist noch fehlerhaft 
- Artikel-Kategorisierung
- KI-Agent für natürliche Fragen

---

## Sprint 3

**Ziel:** AI-Agent für natürliche Sprachfragen, stabile Kassenbon-Validierung, korrekter Excel-Export und erweiterte Telegram-Kommandos.

**Was gebaut wurde:**
- **AI-Agent-Integration:** Textnachrichten (kein Foto, kein Kommando) werden an einen n8n AI Agent weitergeleitet
  - LLM: `Keyvan/german-text-3.1:latest` via Ollama Chat Model Sub-Node
  - Tool-Calling: `search_expenses` durchsucht JSONL-Belege nach Stichworten
  - Window Buffer Memory: Konversations-Kontext (10 Nachrichten pro Chat-ID)
  - Agent-Antwort wird als Telegram-Reply gesendet
- **Reply-Kontext:** Telegram-Replys werden automatisch im Prompt mitgeliefert, damit Folgefragen funktionieren
- **Switch Node Refactoring:** Verkettete IF-Nodes durch einen einzelnen Switch Node ersetzt
  - Outputs: Foto → OCR, `/export` → Export, `/list` → Auflisten, `/delete` → Löschen, `/help` → Hilfe, Default → AI Agent
- **Schema-Fixes für aktuelles n8n (2.20.7):**
  - Switch Node: `typeVersion` 2 → 3 (`rules.values`, `outputKey`, `combinator`, `fallbackOutput`)
  - IF Node „Ist Kassenbon?“: Legacy-Schema → modernes Filter-Schema
- **Kassenbon-Validierung verbessert:** OCR-Prompt prüft, ob überhaupt ein Kassenbon im Bild ist; bei Nein Antwort `NOT_A_RECEIPT`, zusätzlich per IF Node ausgeschlossen
- **Neue Kommandos:** `/list`, `/delete [Jahr] confirm`, `/help`
- **Export-Format verbessert:** CSV → echtes Excel (.xlsx) via nativer `spreadsheetFile` Node
- **Format OCR erweitert:** Steuernummer (`vat_id`) wird in der Telegram-Ausgabe angezeigt

**Neu seit Sprint 2:**
- Natürlichsprachliche Fragen an den AI Agent (vorher nur Fotos und `/export`)
- Stabile Routing-Logik per Switch Node statt IF-Ketten
- Korrekter Excel-Export statt fehlerhaftem CSV
- `/list`, `/delete`, `/help` Kommandos
- Zuverlässige Kassenbon-Erkennung mit `NOT_A_RECEIPT`-Filter

**Technische Entscheidungen:**
- `spreadsheetFile` Node statt manuellem CSV-String → keine Formatierungsfehler, echte .xlsx-Datei
- Switch Node statt IF-Kette → weniger Nodes, klarere Default-Route zum AI Agent
- Moderne n8n-Schemas für Switch/IF Nodes verwenden, da Legacy-Schemas stillschweigend fehlschlagen
- Reply-Kontext als `[Referenzierte Nachricht]: ... / [Meine Frage]: ...` formatieren, damit der Agent Folgefragen versteht

**Offene Punkte:**
- Nur ein einfaches Agent-Tool (`search_expenses`)
- Noch keine Kategorisierung
- Agent-Tools für Statistiken, Vergleiche und Detailabfragen fehlen

---

## Sprint 4

**Ziel:** Vollständigen Feature-Set für den Demo Day finalisieren: erweiterte Agent-Tools, Kategorisierung, Code-Refactoring, GitHub Pages und Demo-Day-Unterlagen.

**Was gebaut wurde:**
- **Erweiterte Agent-Tools:**
  - `get_summary_stats` – Summe, Durchschnitt, Anzahl, teuerster/günstigster Beleg für Jahr oder Monat
  - `compare_periods` – Vergleich der Ausgaben zweier Jahre
  - `top_merchants` – Top 5 Geschäfte nach Gesamtausgaben
  - `get_receipt_by_id` – Details eines einzelnen Belegs inkl. Artikel, Steuernummer, IBAN, Notizen
  - `category_breakdown` – Ausgaben nach Kategorie aufgeschlüsselt
  - `search_expenses` liefert Beleg-IDs zurück und filtert nach `chatId`
- **Kategorisierung:** Belege erhalten beim Speichern eine Kategorie per Keyword-Regeln (Groceries, Restaurant, Transport, Health, Electronics, Clothing, Leisure, Household, Other)
- **Agent-Antworten bereinigt:** Markdown-Sonderzeichen werden vor Telegram-Versand entfernt, `parseMode: none`, Antworten auf 4000 Zeichen gekürzt, System-Prompt auf präzise finale Antworten optimiert
- **Scanning-Fix:** IF-Node „Is Receipt?“ zurück auf `typeVersion: 1`, OCR-Prompt vereinfacht
- **Telegram „Typing“-Indikator:** `sendChatAction` mit `typing` direkt nach dem Trigger für bessere UX
- **Großes Refactoring:**
  - `TelegramReceiptWorkflow.kt` von ~1600 Zeilen auf einen modularen Builder reduziert
  - Node-Definitionen ausgelagert in `AgentNodes.kt`, `CommandAndExportNodes.kt`, `CommonNodes.kt`, `ReceiptNodes.kt`, `ErrorWorkflowNodes.kt`, `WorkflowConnections.kt`, `WorkflowIds.kt`
  - UI-Texte, Node-Namen und OCR-Ausgabe ins Englische übersetzt
- **Dokumentation & Deployment:**
  - Root `README.md` mit Architektur, Setup und Links
  - `docker-compose.yml` für n8n + Ollama
  - GitHub Pages `docs/index.html` und `docs/index-en.html`
  - Neue Screenshots: Workflow-Übersicht, Beispiel-Konversationen, Tool-Nutzung
  - Demo-Day-Unterlagen: Präsentationsnotizen, Demo-Video, Submission-PDF

**Neu seit Sprint 3:**
- Fünf vollständige Agent-Tools statt nur Suche
- Regelbasierte Kategorisierung
- Modulare Kotlin-Codebasis
- Englisch als Projektsprache im UI
- GitHub Pages Projekt-Website
- Docker-Compose-Stack für einfaches Setup

**Technische Entscheidungen:**
- Modularisierung des Workflow-Builders → bessere Wartbarkeit, einfacheres Hinzufügen neuer Nodes
- Keyword-basierte Kategorisierung statt LLM-Kategorisierung → schneller, kostenlos, transparenter
- JSONL bleibt primäre Datenspeicherung → einfach, versionsfähig, ausreichend für Demo-Day-Umfang
- Docker Compose als Standard-Deployment → ein Befehl startet n8n + Ollama

**Learnings:**
- Prompt-Engineering für OCR ist empfindlich: kleine Änderungen können Erkennung komplett beeinflussen
- Tool-Beschreibungen für den AI Agent müssen präzise sein, damit das richtige Tool gewählt wird
- Lokale Modelle funktionieren für praktische Use-Cases, erfordern aber Monitoring und Fallbacks

**Ausblick:**
- Mehrere Benutzer / Chat-IDs sauber trennen
- Echte Datenbank (SQLite/Postgres) statt JSONL für bessere Skalierung
- Dashboard außerhalb von Telegram (Web-UI)
- Evaluation-Framework für OCR-Ausgaben zum messbaren Testen von Prompt-Änderungen
