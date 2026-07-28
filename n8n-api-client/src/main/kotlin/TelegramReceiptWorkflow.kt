import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import nodes.*

/**
 * Deploys the Expense Tracker n8n workflow programmatically.
 */
fun main() {
    val dotenv = dotenv {
        directory = "."
        ignoreIfMalformed = true
        ignoreIfMissing = true
    }

    val n8nUrl = dotenv["N8N_URL"] ?: System.getenv("N8N_URL") ?: "http://localhost:5678"
    val apiKey = dotenv["N8N_API_KEY"] ?: System.getenv("N8N_API_KEY") ?: error("N8N_API_KEY is missing!")
    val botToken = dotenv["TELEGRAM_BOT_TOKEN"] ?: System.getenv("TELEGRAM_BOT_TOKEN") ?: error("TELEGRAM_BOT_TOKEN is missing!")

    val ollamaUrl = dotenv["OLLAMA_URL"] ?: System.getenv("OLLAMA_URL") ?: "http://ollama:11434/api/generate"
    val ollamaBaseUrl = dotenv["OLLAMA_BASE_URL"] ?: System.getenv("OLLAMA_BASE_URL") ?: "http://ollama:11434"
    val ollamaModel = dotenv["OLLAMA_MODEL"] ?: System.getenv("OLLAMA_MODEL") ?: "qwen3-vl:4b"
    val ocrModel = dotenv["OLLAMA_OCR_MODEL"] ?: System.getenv("OLLAMA_OCR_MODEL") ?: "deepseek-ocr:latest"
    val agentModel = dotenv["OLLAMA_AGENT_MODEL"] ?: System.getenv("OLLAMA_AGENT_MODEL") ?: "Keyvan/german-text-3.1:latest"

    val client = N8nClient(baseUrl = n8nUrl, apiKey = apiKey)

    runBlocking {
        try {
            println("Creating Telegram credential...")
            val credentialId = client.findOrCreateTelegramCredential(botToken)
            println("✅ Credential created: $credentialId")

            println("Creating Ollama credential...")
            val ollamaCredentialId = client.findOrCreateOllamaCredential(ollamaBaseUrl)
            println("✅ Ollama credential: $ollamaCredentialId")

            val errorWorkflowName = "Expense Tracker - Error Handler"
            println("Checking for existing error workflow...")
            client.listWorkflows().find { it.name == errorWorkflowName }?.let {
                println("Found [${it.id}]. Deleting old error workflow...")
                client.deleteWorkflow(it.id!!)
                println("✅ Old error workflow deleted.")
            }

            println("Creating error workflow...")
            val errorWorkflow = buildErrorWorkflow(credentialId)
            val errorCreated = client.createFullWorkflow(errorWorkflow)
            val errorWorkflowId = errorCreated.id
            println("✅ Error workflow created: [$errorWorkflowId] ${errorCreated.name}")

            val workflowName = "Expense Tracker"
            println("Checking for existing workflow '$workflowName'...")
            client.listWorkflows().find { it.name == workflowName }?.let {
                println("Found [${it.id}]. Deleting old workflow...")
                client.deleteWorkflow(it.id!!)
                println("✅ Old workflow deleted.")
            }

            println("Creating new workflow...")
            val workflow = buildReceiptValidationWorkflow(credentialId, ollamaCredentialId, ollamaUrl, ollamaModel, ocrModel, agentModel, botToken, errorWorkflowId)
            val created = client.createFullWorkflow(workflow)
            println("✅ Workflow created: [${created.id}] ${created.name}")

            println(
                """

                📋 Workflows created (INACTIVE).
                To test in the n8n UI:
                1. Open the "Expense Tracker" workflow
                2. Click "Execute Workflow"
                3. Send an image to the Telegram bot
                4. After successful testing: enable the workflow (toggle top right)
                5. Enable the "Expense Tracker - Error Handler" workflow to catch global errors
                """.trimIndent()
            )
        } catch (e: N8nApiException) {
            System.err.println("API error: ${e.message} (Status: ${e.statusCode})")
        } catch (e: Exception) {
            System.err.println("Error: ${e.message}")
            e.printStackTrace()
        } finally {
            client.close()
        }
    }
}

fun buildReceiptValidationWorkflow(
    credentialId: String,
    ollamaCredentialId: String,
    ollamaUrl: String,
    model: String,
    ocrModel: String,
    agentModel: String,
    botToken: String,
    errorWorkflowId: String? = null
): WorkflowCreateRequest {
    val ids = WorkflowIds()

    return WorkflowCreateRequest(
        name = "Expense Tracker",
        nodes = listOf(
            telegramTriggerNode(ids, credentialId),
            sendChatActionNode(ids, botToken),
            commandSwitchNode(ids),
            validatingNode(ids, credentialId),
            getFileNode(ids, botToken),
            downloadImageNode(ids, botToken),
            toBase64Node(ids),
            ocrNode(ids, ollamaUrl, ocrModel),
            timeoutNode(ids, credentialId),
            ifReceiptNode(ids),
            formatOcrNode(ids),
            ocrResultNode(ids, credentialId),
            restoreBinaryNode(ids),
            saveImageNode(ids),
            saveJsonNode(ids),
            noReceiptNode(ids, credentialId),
            exportRowsNode(ids),
            xlsxExportNode(ids),
            sendExcelNode(ids, credentialId),
            listFilesNode(ids),
            listResponseNode(ids, credentialId),
            deleteFilesNode(ids),
            deleteResponseNode(ids, credentialId),
            helpGeneratorNode(ids),
            helpResponseNode(ids, credentialId),
            aiAgentNode(ids),
            ollamaChatModelNode(ids, ollamaCredentialId, agentModel),
            searchExpensesToolNode(ids),
            getSummaryStatsToolNode(ids),
            comparePeriodsToolNode(ids),
            topMerchantsToolNode(ids),
            getReceiptByIdToolNode(ids),
            categoryBreakdownToolNode(ids),
            agentMemoryNode(ids),
            agentResponseNode(ids, credentialId)
        ),
        connections = buildWorkflowConnections(),
        settings = buildJsonObject {
            put("executionOrder", "v1")
            if (errorWorkflowId != null) put("errorWorkflow", errorWorkflowId)
        }
    )
}

fun buildErrorWorkflow(credentialId: String): WorkflowCreateRequest {
    val triggerNode = errorTriggerNode()
    val responseNode = errorResponseNode(credentialId)

    return WorkflowCreateRequest(
        name = "Expense Tracker - Error Handler",
        nodes = listOf(triggerNode, responseNode),
        connections = buildErrorConnections(),
        settings = buildJsonObject { put("executionOrder", "v1") }
    )
}
