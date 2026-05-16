import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking

fun main() {
    val dotenv = dotenv {
        directory = "."
        ignoreIfMalformed = true
        ignoreIfMissing = true
    }

    val n8nUrl = dotenv["N8N_URL"] ?: System.getenv("N8N_URL") ?: "http://localhost:5678"
    val apiKey = dotenv["N8N_API_KEY"] ?: System.getenv("N8N_API_KEY") ?: error("N8N_API_KEY ist weder in .env noch als Umgebungsvariable gesetzt!")

    val client = N8nClient(baseUrl = n8nUrl, apiKey = apiKey)

    runBlocking {
        try {
            println("=== Workflows abrufen ===")
            val workflows = client.listWorkflows()
            workflows.forEach { w ->
                println("- [${w.id}] ${w.name} (active=${w.active})")
            }

            println("\n=== Neuen Workflow erstellen ===")
            val newWorkflow = client.createWorkflow("API-Test-Workflow")
            println("Erstellt: [${newWorkflow.id}] ${newWorkflow.name}")

            println("\n=== Workflow deaktivieren / aktivieren ===")
            val deactivated = client.deactivateWorkflow(newWorkflow.id!!)
            println("Deaktiviert: ${deactivated.active}")

            val activated = client.activateWorkflow(newWorkflow.id)
            println("Aktiviert: ${activated.active}")

            println("\n=== Workflow loschen ===")
            val deleted = client.deleteWorkflow(newWorkflow.id)
            println("Geloscht: $deleted")

            println("\n=== Executions abrufen ===")
            val executions = client.listExecutions()
            executions.take(5).forEach { e ->
                println("- [${e.id}] Workflow=${e.workflowId} status=${e.status}")
            }
        } catch (e: N8nApiException) {
            System.err.println("API-Fehler: ${e.message} (Status: ${e.statusCode})")
        } catch (e: Exception) {
            System.err.println("Fehler: ${e.message}")
            e.printStackTrace()
        } finally {
            client.close()
        }
    }
}
