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
