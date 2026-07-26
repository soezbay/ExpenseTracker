**Für Moodle Reviewer:**
Im Sprint 1 wurde ein Kotlin-API-Client entwickelt, der automatisch einen n8n-Workflow auf einem
HomeServer erstellt und deployed, um Telegram-Fotos per Ollama als Kassenbon zu klassifizieren.
Die Telegram-Integration umfasst Foto-Empfang, Zwischenantworten und Bild-Download mit base64-Konvertierung
für das lokale Vision-Modell Gemma4. Das System beinhaltet Timeout-Handling mit dedizierter Fehler-Node sowie
automatische Credential-Wiederverwendung. Ein aktuelles Problem ist, dass die HomeServer GPU bei
Bildberechnungen überhitzt, wodurch Anfragen limitiert werden müssen.