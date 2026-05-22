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
