# Demo Day Präsentation – Expense Tracker
**Dauer:** 7 Minuten  
**Sprache:** Deutsch (oder Englisch anpassbar)  
**Format:** 4 Abschnitte mit Sprechernotizen & Zeitbudget

---

## 1. Problem & Lösung (1 Minute)

### Slide / Inhalt
**Titel:** „Kassenbons nerven – wir machen sie gesprächig.“

**Problem:**
- Jeder sammelt Kassenbons, Rechnungen und Belege.
- Man verliert den Überblick, vergisst Ausgaben, und am Ende des Monats ist das Excel-Chaos groß.
- Bestehende Apps sind oft überladen, teuer oder nicht Datenschutz-freundlich.

**Lösung:**
- Ein Telegram-Bot, der Belege per Foto entgegennimmt, per OCR ausliest und strukturiert speichert.
- Ein smarter AI-Agent beantwortet Fragen zu Ausgaben, liefert Statistiken, Vergleiche und Kategorien.
- Alles läuft lokal (Ollama + n8n) – Daten bleiben beim User.

### Sprechernotizen (ca. 60 Sek.)
> „Stell dir vor, du kommst vom Einkaufen. Ein Foto im Telegram-Chat, und der Bot sagt dir: „Rewe, 42,30 €, Lebensmittel, gespeichert.“ Später fragst du: „Wie viel habe ich diesen Monat ausgegeben?“ oder „Was waren meine Top-Händler?“ – und der Agent antwortet sofort. Das ist unser Expense Tracker.“

---

## 2. Live Demo / Demo-Video (3–4 Minuten)

### Szenario A: Beleg fotografieren (1–1,5 Min.)
**Was gezeigt wird:**
1. User sendet Foto eines Kassenbons an den Telegram-Bot.
2. Bot lädt Bild, startet OCR (Ollama Vision/OCR-Modell).
3. Bot extrahiert Händler, Betrag, MwSt, Artikel und Kategorie.
4. Bot speichert Beleg als JSONL + Bild im Dateisystem.
5. Bot antwortet mit formatierter Zusammenfassung.

**Sprechernotizen:**
> „Ich schicke einfach ein Foto. Der Bot erkennt den Händler, summiert Netto und MwSt, ordnet automatisch die Kategorie „Lebensmittel“ zu und legt alles ab. Kein Tippen, kein Template.“

### Szenario B: Natürlichsprachliche Abfragen (1–1,5 Min.)
**Was gezeigt wird:**
1. User schreibt: „Wie viel habe ich im Juni ausgegeben?“
2. AI-Agent ruft `get_summary_stats` auf.
3. Antwort: „Im Juni 2026: 14 Belege, 623,80 € gesamt, Ø 44,56 €.“

**Weitere Prompts zum Vorführen:**
- „Vergleiche Juni mit Mai."
- „Top 5 Händler dieses Jahr."
- „Zeig mir Beleg #123456789."
- „Wie viel habe ich für Lebensmittel ausgegeben?"

**Sprechernotizen:**
> „Der Agent versteht nicht nur Befehle, sondern echte Fragen. Er entscheidet selbst, welches Tool er braucht – Statistik, Zeitraumvergleich, Händler-Ranking oder Detailabfrage per Beleg-ID.“

### Szenario C: Kategorie-Übersicht (30–45 Sek.)
**Was gezeigt wird:**
- Frage: „Wie verteilen sich meine Ausgaben auf Kategorien?"
- Agent gruppiert Belege nach Kategorien (Lebensmittel, Transport, Restaurant etc.).
- Darstellung als Text-Liste oder optionaler kleiner Balken (ASCII/Emoji).

**Sprechernotizen:**
> „Kategorisierung passiert regelbasiert lokal, nicht durch einen teuren Cloud-Dienst. Das ist datenschonend und schnell.“

### Szenario D: Export (optional, 30 Sek.)
**Was gezeigt wird:**
- Befehl `/export 2026` im Chat.
- Bot generiert XLSX/CSV aus allen Belegen und sendet die Datei.

**Sprechernotizen:**
> „Für die Steuer oder das Haushaltsbuch: Ein Befehl, und alle Belege des Jahres kommen als Excel-Datei.“

---

## 3. Architektur & wichtige technische Entscheidungen (1 Minute)

### Slide / Inhalt
**Diagramm (Text-Version):**

```
Telegram Bot
    ↓
n8n Workflow (Trigger → OCR → Validierung → Speichern → Agent)
    ↓
Ollama (lokales LLM/Vision)  →  OCR + AI-Agent
    ↓
JSONL-Dateien (receipts_YYYY.jsonl) + Bilder (bin/)
```

**Komponenten:**
- **n8n** als Workflow-Engine, komplett per Kotlin-API-Client aufgebaut.
- **Ollama** für OCR (Vision-Modelle) und den AI-Agent (Text-Modell).
- **Telegram** als UI – jeder kennt es, keine App-Installation.
- **JSONL** als einfache, versionsfähige Datenspeicherung.
- **Kotlin-Client** baut Workflows programmatisch, reproduzierbar und deploybar.

**Wichtige technische Entscheidungen:**
1. **Lokale LLMs statt Cloud:** Datenschutz, keine API-Kosten, volle Kontrolle.
2. **Telegram als Interface:** Null Installationsaufwand, Chat ist natürliche UX.
3. **Programmatischer n8n-Workflow:** Alles in Kotlin beschrieben – kein Klicken im UI, alles in Git.
4. **Tool-aufrufender Agent:** Der Agent entscheidet selbst, wann er `get_summary_stats`, `compare_periods`, `top_merchants`, `get_receipt_by_id` oder `category_breakdown` nutzt.
5. **Regelbasierte Kategorisierung:** Schnell, transparent, anpassbar ohne Neutraining.

### Sprechernotizen (ca. 60 Sek.)
> „Der Clou ist, dass der gesamte Workflow in Kotlin generiert wird. Wir können ihn versionieren, reviewen und mit einem Befehl neu deployen. Der Agent ist ein Tool-Calling-Agent: Er sieht die verfügbaren Funktionen und wählt passend zur Frage selbst aus. Und weil alles lokal läuft, bleiben meine Finanzdaten bei mir.“

---

## 4. Reflexion (1 Minute)

### Slide / Inhalt
**Titel:** „Was wir gelernt haben & was wir anders machen würden“

**Biggest Learning:**
- Prompt-Engineering für OCR ist knifflig: Kleine Änderungen am Prompt können den Unterschied zwischen „erkennt alles“ und „erkennt nichts“ ausmachen.
- Tool-Calling-Agents sind mächtig, aber die Tool-Beschreibungen müssen präzise sein.
- Lokale Modelle funktionieren für praktische Use-Cases, erfordern aber Monitoring und Fallbacks.

**Was wir anders machen würden:**
- Früher mit einer realen Datenbank arbeiten (z.B. SQLite oder Postgres) statt JSONL, um größere Datenmengen und Aggregationen besser zu skalieren.
- Evaluation-Framework für OCR-Ausgaben einbauen, um Prompt-Änderungen messbar zu bewerten.
- Mehr Zeit in die UI/UX-Struktur des Telegram-Chats stecken (z.B. Inline-Buttons, Bestätigungen).

**Ausblick:**
- Mehrere Benutzer / Chat-IDs sauber trennen.
- Dashboard außerhalb von Telegram (z.B. Web-UI).
- Automatische Mahnungen bei wiederkehrenden Zahlungen.

### Sprechernotizen (ca. 60 Sek.)
> „Das größte Learning: Prompt-Engineering für OCR ist kein Glücksspiel, aber fast. Ein Satz zu viel, und das Modell ignoriert den Beleg. Was wir heute anders machen würden: Echte Datenbank früh einsetzen und ein automatisiertes Test-Setup für Belege bauen, damit wir Prompt-Änderungen wirklich vergleichen können. Der Ausblick ist klar: Mehrere Nutzer, ein Dashboard, und der Bot wird immer mehr zum persönlichen Finanzassistenten.“

---

## Tipps für die Präsentation

- **Demo-Video als Backup:** Wenn Live-LLM-Latenzen oder OCR-Probleme drohen, zeige ein 30-Sekunden-Video pro Szenario.
- **Wirklich nur 7 Minuten:** Üben mit Stoppuhr. Demo-Teil ist das Herzstück und darf die 4 Minuten ausnutzen.
- **Einen echten Beleg vorbereiten:** Ein Foto, das zuverlässig erkannt wird, z.B. REWE, Aldi oder eine Restaurant-Rechnung.
- **Nicht jedes Tool zeigen:** 2–3 starke Beispiele sind besser als alle fünf Tools durchzuklicken.
- **Call to Action am Ende:** „Probiert es aus – der Bot wartet im Telegram-Chat.“
