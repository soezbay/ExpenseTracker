import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.util.*

/**
 * Erstellt einen n8n-Workflow, der auf Telegram-Bilder reagiert und Kassenbons weiterleitet.
 *
 * WICHTIG: Bevor du das ausfuhrst, musst du in n8n ein Telegram-Credential erstellen:
 * 1. n8n UI offnen -> Settings -> Credentials -> Add Credential
 * 2. Type: "Telegram" -> Bot Token eingeben (aus deiner .env)
 * 3. Die Credential-ID notieren (z.B. "abc123-def...")
 * 4. Unten in [CREDENTIAL_ID] eintragen
 */
fun main() {
    val dotenv = dotenv {
        directory = "."
        ignoreIfMalformed = true
        ignoreIfMissing = true
    }

    val n8nUrl = dotenv["N8N_URL"] ?: System.getenv("N8N_URL") ?: "http://localhost:5678"
    val apiKey = dotenv["N8N_API_KEY"] ?: System.getenv("N8N_API_KEY") ?: error("N8N_API_KEY fehlt!")
    val botToken = dotenv["TELEGRAM_BOT_TOKEN"] ?: System.getenv("TELEGRAM_BOT_TOKEN") ?: error("TELEGRAM_BOT_TOKEN fehlt!")

    // === HIER ANPASSEN ===
    val telegramCredentialId = "5HZvjIhAH6nQ8"  // Aus n8n Credentials
    val llmApiUrl = "DEINE_LLM_URL"             // z.B. http://homeserver:11434/api/generate
    // =====================

    if (llmApiUrl == "DEINE_LLM_URL") {
        println("⚠️  FEHLER: Bitte trage die URL deiner lokalen LLM-API ein.")
        println("   Beispiel: http://192.168.1.100:11434/api/generate")
        return
    }

    val client = N8nClient(baseUrl = n8nUrl, apiKey = apiKey)

    runBlocking {
        try {
            println("Erstelle Kassenbon-Workflow...")

            val workflow = buildReceiptWorkflow(telegramCredentialId, llmApiUrl)
            val created = client.createFullWorkflow(workflow)
            println("✅ Workflow erstellt: [${created.id}] ${created.name}")

            println("Aktiviere Workflow...")
            val activated = client.activateWorkflow(created.id!!)
            println("✅ Workflow aktiv: ${activated.active}")

            println("""
                
                🤖 Workflow aktiv!
                Sende ein Foto an deinen Telegram Bot.
                Das Bild wird an deine lokale LLM gesendet: $llmApiUrl
            """.trimIndent())
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

/**
 * Baut den Workflow-JSON fur den Kassenbon-Weiterleiter.
 */
fun buildReceiptWorkflow(credentialId: String, llmApiUrl: String): WorkflowCreateRequest {
    val triggerId = UUID.randomUUID().toString().replace("-", "").take(8)
    val ifId = UUID.randomUUID().toString().replace("-", "").take(8)
    val sendId = UUID.randomUUID().toString().replace("-", "").take(8)

    val triggerNode = buildJsonObject {
        put("id", triggerId)
        put("name", "Telegram Trigger")
        put("type", "n8n-nodes-base.telegramTrigger")
        put("typeVersion", 1.1)
        put("position", buildJsonArray { add(250); add(300) })
        put("webhookId", triggerId)
        put("parameters", buildJsonObject {
            put("updates", buildJsonArray { add("message") })
            put("additionalFields", buildJsonObject {})
        })
        put("credentials", buildJsonObject {
            put("telegramApi", buildJsonObject {
                put("id", credentialId)
                put("name", "Telegram Bot")
            })
        })
    }

    val ifNode = buildJsonObject {
        put("id", ifId)
        put("name", "Foto vorhanden?")
        put("type", "n8n-nodes-base.if")
        put("typeVersion", 2)
        put("position", buildJsonArray { add(500); add(300) })
        put("parameters", buildJsonObject {
            put("conditions", buildJsonObject {
                put("options", buildJsonObject {
                    put("caseSensitive", true)
                    put("leftValue", "")
                    put("looseTypeValidation", true)
                })
                put("conditions", buildJsonArray {
                    add(buildJsonArray {
                        add(buildJsonObject {
                            put("id", UUID.randomUUID().toString())
                            put("leftValue", "={{ \$json.message.photo }}")
                            put("rightValue", "")
                            put("operator", buildJsonObject {
                                put("type", "exists")
                                put("operation", "exists")
                            })
                        })
                    })
                })
            })
        })
    }

    val httpNode = buildJsonObject {
        put("id", sendId)
        put("name", "An LLM senden")
        put("type", "n8n-nodes-base.httpRequest")
        put("typeVersion", 4.2)
        put("position", buildJsonArray { add(750); add(300) })
        put("parameters", buildJsonObject {
            put("method", "POST")
            put("url", llmApiUrl)
            put("sendBody", true)
            put("contentType", "json")
            // HINWEIS: Der Body muss an deine LLM-API angepasst werden!
            // Das hier ist das Ollama-Format. Anderenfalls passe die Felder an.
            put("body", buildJsonObject {
                put("model", "llava")
                put("prompt", "Analysiere diesen Kassenbon. Liste alle Artikel, Preise und das Datum auf.")
                put("stream", false)
                put("images", buildJsonArray {
                    add("={{ \$json.message.photo[\$json.message.photo.length - 1].file_id }}")
                })
            })
        })
    }

    val connections = buildJsonObject {
        put("Telegram Trigger", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject {
                        put("node", "Foto vorhanden?")
                        put("type", "main")
                        put("index", 0)
                    })
                })
            })
        })
        put("Foto vorhanden?", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {})  // false branch (leer)
                add(buildJsonArray {    // true branch
                    add(buildJsonObject {
                        put("node", "An LLM senden")
                        put("type", "main")
                        put("index", 0)
                    })
                })
            })
        })
    }

    return WorkflowCreateRequest(
        name = "Kassenbon an LLM",
        nodes = listOf(triggerNode, ifNode, httpNode),
        connections = connections
    )
}
