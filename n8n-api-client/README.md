# n8n API Client (Kotlin)

Ein Kotlin-Projekt zum Steuern von n8n uber die REST API.

## API-Endpunkte

| Methode | Beschreibung |
|---------|-------------|
| `listWorkflows()` | Alle Workflows auflisten |
| `getWorkflow(id)` | Einzelnen Workflow abrufen |
| `createWorkflow(name)` | Workflow erstellen |
| `updateWorkflow(id, workflow)` | Workflow aktualisieren |
| `deleteWorkflow(id)` | Workflow loschen |
| `activateWorkflow(id)` | Workflow aktivieren |
| `deactivateWorkflow(id)` | Workflow deaktivieren |
| `executeWorkflow(id)` | Workflow manuell ausfuhren |
| `listExecutions()` | Alle Executions auflisten |
| `getExecution(id)` | Einzelne Execution abrufen |

## Konfiguration

1. In n8n einen API-Key erstellen: **Settings > API**
2. Umgebungsvariablen setzen:
   - `N8N_URL` (z.B. `http://localhost:5678`)
   - `N8N_API_KEY`

## Ausfuhren

```bash
./gradlew run
```

Oder in IntelliJ IDEA: `Main.kt` > Rechtsklick > **Run**.
