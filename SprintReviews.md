# Sprint Reviews

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

**Für Moodle Reviewer:**
Im Sprint 1 wurde ein Kotlin-API-Client entwickelt, der automatisch einen n8n-Workflow auf einem HomeServer erstellt und deployed, um Telegram-Fotos per Ollama als Kassenbon zu klassifizieren. Die Telegram-Integration umfasst Foto-Empfang, Zwischenantworten und Bild-Download mit base64-Konvertierung für das lokale Vision-Modell Gemma4. Das System beinhaltet Timeout-Handling mit dedizierter Fehler-Node sowie automatische Credential-Wiederverwendung. Ein aktuelles Problem ist, dass die HomeServer GPU bei Bildberechnungen überhitzt, wodurch Anfragen limitiert werden müssen.

---
