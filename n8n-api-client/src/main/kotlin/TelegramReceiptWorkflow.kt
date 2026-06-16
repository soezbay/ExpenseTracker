import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.util.*

/**
 * Erstellt einen n8n-Workflow:
 * 1. Telegram-Bild empfangen
 * 2. An Ollama (dreammon) senden zur Validierung
 * 3. Wenn kein Kassenbon/Rechnung -> Telegram-Benachrichtigung
 * 4. Wenn Kassenbon/Rechnung -> "Als Kassenbon/Rechnung validiert"
 *
 * Der Bot Token wird aus der .env gelesen und das Telegram-Credential
 * automatisch in n8n erstellt. Keine manuelle Einrichtung nötig.
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

    // === KONFIGURATION ===
    val ollamaUrl = dotenv["OLLAMA_URL"] ?: System.getenv("OLLAMA_URL") ?: "http://ollama:11434/api/generate"
    val ollamaBaseUrl = dotenv["OLLAMA_BASE_URL"] ?: System.getenv("OLLAMA_BASE_URL") ?: "http://ollama:11434"
    val ollamaModel = dotenv["OLLAMA_MODEL"] ?: System.getenv("OLLAMA_MODEL") ?: "qwen3-vl:4b"
    val ocrModel = dotenv["OLLAMA_OCR_MODEL"] ?: System.getenv("OLLAMA_OCR_MODEL") ?: "deepseek-ocr:latest"
    val agentModel = dotenv["OLLAMA_AGENT_MODEL"] ?: System.getenv("OLLAMA_AGENT_MODEL") ?: "Keyvan/german-text-3.1:latest"
    // =====================

    val client = N8nClient(baseUrl = n8nUrl, apiKey = apiKey)

    runBlocking {
        try {
            println("Erstelle Telegram Credential...")
            val credentialId = client.findOrCreateTelegramCredential(botToken)
            println("✅ Credential erstellt: $credentialId")

            println("Erstelle Ollama Credential...")
            val ollamaCredentialId = client.findOrCreateOllamaCredential(ollamaBaseUrl)
            println("✅ Ollama Credential: $ollamaCredentialId")

            val workflowName = "Expense Tracker"
            println("Prüfe auf vorhandenen Workflow '$workflowName'...")
            val existing = client.listWorkflows().find { it.name == workflowName }
            if (existing != null) {
                println("Gefunden [${existing.id}]. Lösche alten Workflow...")
                client.deleteWorkflow(existing.id!!)
                println("✅ Alten Workflow gelöscht.")
            }

            println("Erstelle neuen Workflow...")
            val workflow = buildReceiptValidationWorkflow(credentialId, ollamaCredentialId, ollamaUrl, ollamaModel, ocrModel, agentModel, botToken)
            val created = client.createFullWorkflow(workflow)
            println("✅ Workflow erstellt: [${created.id}] ${created.name}")

            println("Prüfe auf vorhandenen Error Workflow...")
            val errorWorkflowName = "Expense Tracker - Error Handler"
            val existingError = client.listWorkflows().find { it.name == errorWorkflowName }
            if (existingError != null) {
                println("Gefunden [${existingError.id}]. Lösche alten Error Workflow...")
                client.deleteWorkflow(existingError.id!!)
                println("✅ Alten Error Workflow gelöscht.")
            }

            println("Erstelle Error Workflow...")
            val errorWorkflow = buildErrorWorkflow(credentialId)
            val errorCreated = client.createFullWorkflow(errorWorkflow)
            println("✅ Error Workflow erstellt: [${errorCreated.id}] ${errorCreated.name}")

            println("""

                📋 Workflows erstellt (INAKTIV).
                Zum Testen in n8n UI:
                1. "Expense Tracker" Workflow öffnen
                2. Auf "Execute Workflow" klicken
                3. Bild an Telegram Bot senden
                4. Nach erfolgreichem Test: Workflow aktivieren (Toggle oben rechts)
                5. "Expense Tracker - Error Handler" Workflow aktivieren (fängt globale Fehler ab)
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

fun buildReceiptValidationWorkflow(credentialId: String, ollamaCredentialId: String, ollamaUrl: String, model: String, ocrModel: String, agentModel: String, botToken: String): WorkflowCreateRequest {
    val triggerId       = uuidShort()
    val validatingId    = uuidShort()
    val getFileId       = uuidShort()
    val downloadId      = uuidShort()
    val toBase64Id      = uuidShort()
    val ifReceiptId     = uuidShort()
    val noReceiptId     = uuidShort()
    val timeoutId       = uuidShort()
    val ocrId           = uuidShort()
    val formatId        = uuidShort()
    val ocrResultId     = uuidShort()
    val saveImageId     = uuidShort()
    val restoreBinaryId = uuidShort()
    val jsonSaveId      = uuidShort()
    val commandSwitchId = uuidShort()
    val exportCsvId     = uuidShort()
    val sendCsvId       = uuidShort()
    val aiAgentId       = uuidShort()
    val ollamaChatId    = uuidShort()
    val agentToolId     = uuidShort()
    val agentMemoryId   = uuidShort()
    val agentResponseId = uuidShort()
    val listFilesId     = uuidShort()
    val listResponseId  = uuidShort()
    val deleteFilesId   = uuidShort()
    val deleteResponseId = uuidShort()
    val helpNodeId      = uuidShort()
    val helpResponseId  = uuidShort()

    // Node 1: Telegram Trigger
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

    // Node 3: Validating photo (Switch output 0 = Foto vorhanden)
    val validatingNode = buildJsonObject {
        put("id", validatingId)
        put("name", "Antwort: Validating photo")
        put("type", "n8n-nodes-base.telegram")
        put("typeVersion", 1.1)
        put("position", buildJsonArray { add(750); add(300) })
        put("parameters", buildJsonObject {
            put("operation", "sendMessage")
            put("chatId", "={{ \$('Telegram Trigger').item.json.message.chat.id }}")
            put("text", "Validating photo.. please wait.")
            put("additionalFields", buildJsonObject {
                put("reply_to_message_id", "={{ parseInt(\$('Telegram Trigger').item.json.message.message_id) }}")
                put("appendAttribution", false)
            })
        })
        put("credentials", buildJsonObject {
            put("telegramApi", buildJsonObject {
                put("id", credentialId)
                put("name", "Telegram Bot")
            })
        })
    }

    // Node 4a: HTTP – Telegram getFile (file_id → file_path)
    val getFileNode = buildJsonObject {
        put("id", getFileId)
        put("name", "Telegram getFile")
        put("type", "n8n-nodes-base.httpRequest")
        put("typeVersion", 4.2)
        put("position", buildJsonArray { add(750); add(300) })
        put("parameters", buildJsonObject {
            put("method", "GET")
            put("url", "https://api.telegram.org/bot$botToken/getFile")
            put("sendQuery", true)
            put("queryParameters", buildJsonObject {
                put("parameters", buildJsonArray {
                    add(buildJsonObject {
                        put("name", "file_id")
                        put("value", "={{ \$('Telegram Trigger').item.json.message.photo[\$('Telegram Trigger').item.json.message.photo.length - 1].file_id }}")
                    })
                })
            })
        })
    }

    // Node 4b: HTTP – Bild als Binary herunterladen
    val downloadNode = buildJsonObject {
        put("id", downloadId)
        put("name", "Bild herunterladen")
        put("type", "n8n-nodes-base.httpRequest")
        put("typeVersion", 4.2)
        put("position", buildJsonArray { add(1000); add(300) })
        put("parameters", buildJsonObject {
            put("method", "GET")
            put("url", "={{ `https://api.telegram.org/file/bot$botToken/` + \$json.result.file_path }}")
            put("responseFormat", "file")
            put("options", buildJsonObject {})
        })
    }

    // Node 4c: Code – Binary zu base64 String
    val toBase64Node = buildJsonObject {
        put("id", toBase64Id)
        put("name", "Zu Base64")
        put("type", "n8n-nodes-base.code")
        put("typeVersion", 2)
        put("position", buildJsonArray { add(1125); add(300) })
        put("parameters", buildJsonObject {
            put("language", "javaScript")
            put("jsCode", "const items = \$input.all();\n" +
                "const item = items[0];\n" +
                "const binaryKey = Object.keys(item.binary)[0];\n" +
                "const binaryData = item.binary[binaryKey];\n" +
                "const buffer = await this.helpers.getBinaryDataBuffer(0, binaryKey);\n" +
                "const base64 = buffer.toString('base64');\n" +
                "const trigger = \$('Telegram Trigger').item.json.message;\n" +
                "const chatId = trigger.chat.id;\n" +
                "const messageId = trigger.message_id;\n" +
                "const receiptId = chatId + '_' + messageId + '_' + Math.random().toString(36).substring(2, 8);\n" +
                "return [{ json: { imageBase64: base64, chatId, messageId, receiptId, mimeType: binaryData.mimeType || 'image/jpeg' } }];")
        })
    }

    // Node 5b: Timeout-Fehler → Telegram Benachrichtigung
    val timeoutNode = buildJsonObject {
        put("id", timeoutId)
        put("name", "Antwort: Timeout")
        put("type", "n8n-nodes-base.telegram")
        put("typeVersion", 1.1)
        put("position", buildJsonArray { add(1500); add(600) })
        put("parameters", buildJsonObject {
            put("operation", "sendMessage")
            put("chatId", "={{ \$('Telegram Trigger').item.json.message.chat.id }}")
            put("text", "Die Verarbeitung hat zu lange gedauert. Bitte versuche es erneut.")
            put("additionalFields", buildJsonObject {
                put("reply_to_message_id", "={{ parseInt(\$('Telegram Trigger').item.json.message.message_id) }}")
                put("appendAttribution", false)
            })
        })
        put("credentials", buildJsonObject {
            put("telegramApi", buildJsonObject {
                put("id", credentialId)
                put("name", "Telegram Bot")
            })
        })
    }

    // Node 5c: Code – OCR JSON zu lesbarem Text formatieren
    val formatNode = buildJsonObject {
        put("id", formatId)
        put("name", "Format OCR")
        put("type", "n8n-nodes-base.code")
        put("typeVersion", 2)
        put("position", buildJsonArray { add(1750); add(150) })
        put("parameters", buildJsonObject {
            put("language", "javaScript")
            put("jsCode",
                "const raw = \$input.first().json.response;\n" +
                "let formatted;\n" +
                "try {\n" +
                "  let jsonStr = raw.trim();\n" +
                "  if (jsonStr.startsWith('```')) {\n" +
                "    jsonStr = jsonStr.replace(/^```(?:json)?\\n?/, '').replace(/\\n?```\\$/, '');\n" +
                "  }\n" +
                "  const data = JSON.parse(jsonStr);\n" +
                "  const lines = [];\n" +
                "  if (data.sender?.name) lines.push(data.sender.name);\n" +
                "  if (data.sender?.address) lines.push(data.sender.address);\n" +
                "  const vatId = data.sender?.vat_id || data.sender?.vatid || '';\n" +
                "  if (vatId) lines.push('Steuernummer: ' + vatId);\n" +
                "  const date = data.invoice_date || data.invoicedate;\n" +
                "  if (date) lines.push('Datum: ' + date);\n" +
                "  const nr = data.invoice_number || data.invoicenumber;\n" +
                "  if (nr) lines.push('Beleg-Nr: ' + nr);\n" +
                "  lines.push('');\n" +
                "  lines.push('--- Artikel ---');\n" +
                "  const allItems = data.line_items || data.lineitems || [];\n" +
                "  const items = allItems.filter(i => (i.unit_price_net || i.unitpricenet) !== null && (i.unit_price_net || i.unitpricenet) !== undefined);\n" +
                "  for (const item of items) {\n" +
                "    const qty = item.quantity ? item.quantity + 'x ' : '';\n" +
                "    const amt = (item.amount_net ?? item.amountnet);\n" +
                "    const amtStr = amt !== null && amt !== undefined ? ' ' + amt.toFixed(2) + ' ' + (data.currency || 'EUR') : '';\n" +
                "    lines.push(qty + item.description + amtStr);\n" +
                "  }\n" +
                "  lines.push('');\n" +
                "  const total = data.amount_total ?? data.amounttotal;\n" +
                "  if (total !== null && total !== undefined) lines.push('Gesamt: ' + total.toFixed(2) + ' ' + (data.currency || 'EUR'));\n" +
                "  const vat = data.amount_vat ?? data.amountvat;\n" +
                "  if (vat) lines.push('MwSt: ' + vat.toFixed(2) + ' ' + (data.currency || 'EUR'));\n" +
                "  formatted = lines.join('\\n');\n" +
                "} catch (e) {\n" +
                "  formatted = raw;\n" +
                "}\n" +
                "return [{ json: { response: formatted } }];")
        })
    }

    // Node 6: IF – Ist Kassenbon? (true=index 0 → JA, false=index 1 → NEIN)
    val ifReceiptNode = buildJsonObject {
        put("id", ifReceiptId)
        put("name", "Ist Kassenbon?")
        put("type", "n8n-nodes-base.if")
        put("typeVersion", 1)
        put("position", buildJsonArray { add(1500); add(300) })
        put("parameters", buildJsonObject {
            put("conditions", buildJsonObject {
                put("string", buildJsonArray {
                    add(buildJsonObject {
                        put("value1", "={{ \$json.response.trim() }}")
                        put("operation", "isNotEmpty")
                    })
                })
            })
        })
    }

    // Node 7b: Ollama OCR – Validierung + Extraktion in einem Schritt
    val ocrPrompt = when {
        ocrModel.startsWith("Keyvan/german-ocr") || ocrModel.startsWith("german-ocr") -> "Extrahiere die Rechnung im Bild als JSON."
        ocrModel.startsWith("deepseek-ocr")                                   -> "Extract the text in the image."
        else                                                                   -> "Extract the text in the image."
    }
    val ocrNode = buildJsonObject {
        put("id", ocrId)
        put("name", "Ollama OCR")
        put("type", "n8n-nodes-base.httpRequest")
        put("typeVersion", 4.2)
        put("position", buildJsonArray { add(1250); add(300) })
        put("parameters", buildJsonObject {
            put("method", "POST")
            put("url", ollamaUrl)
            put("sendBody", true)
            put("specifyBody", "json")
            put("jsonBody", "={{ JSON.stringify({ model: \"$ocrModel\", prompt: \"$ocrPrompt\", stream: false, images: [\$json.imageBase64] }) }}")
            put("options", buildJsonObject {
                put("timeout", 300000)
            })
            put("onError", "continueErrorOutput")
        })
    }

    // Node 7c: OCR Ergebnis → Telegram
    val ocrResultNode = buildJsonObject {
        put("id", ocrResultId)
        put("name", "Antwort: OCR Ergebnis")
        put("type", "n8n-nodes-base.telegram")
        put("typeVersion", 1.1)
        put("position", buildJsonArray { add(2000); add(150) })
        put("parameters", buildJsonObject {
            put("operation", "sendMessage")
            put("chatId", "={{ \$('Telegram Trigger').item.json.message.chat.id }}")
            put("text", "={{ \$('Format OCR').item.json.response }}")
            put("additionalFields", buildJsonObject {
                put("reply_to_message_id", "={{ parseInt(\$('Telegram Trigger').item.json.message.message_id) }}")
                put("appendAttribution", false)
            })
        })
        put("credentials", buildJsonObject {
            put("telegramApi", buildJsonObject {
                put("id", credentialId)
                put("name", "Telegram Bot")
            })
        })
    }

    // Node 8: Binary wiederherstellen (für Write Binary File)
    val restoreBinaryNode = buildJsonObject {
        put("id", restoreBinaryId)
        put("name", "Restore Binary")
        put("type", "n8n-nodes-base.code")
        put("typeVersion", 2)
        put("position", buildJsonArray { add(1875); add(300) })
        put("parameters", buildJsonObject {
            put("language", "javaScript")
            put("jsCode",
                "const meta = \$('Zu Base64').item.json;\n" +
                "const binaryData = \$('Bild herunterladen').item.binary;\n" +
                "const key = Object.keys(binaryData)[0];\n" +
                "return [{ json: { receiptId: meta.receiptId, mimeType: meta.mimeType }, binary: { data: binaryData[key] } }];")
        })
    }

    // Node 8a: Bild lokal speichern (fs.writeFileSync)
    val saveImageNode = buildJsonObject {
        put("id", saveImageId)
        put("name", "Bild speichern")
        put("type", "n8n-nodes-base.code")
        put("typeVersion", 2)
        put("position", buildJsonArray { add(2000); add(300) })
        put("parameters", buildJsonObject {
            put("language", "javaScript")
            put("jsCode",
                "const fs = require('fs');\n" +
                "const path = require('path');\n" +
                "const meta = \$input.first().json;\n" +
                "const dir = '/home/node/.n8n/expenseTracker/bin';\n" +
                "if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });\n" +
                "const ext = meta.mimeType === 'image/png' ? '.png' : '.jpg';\n" +
                "const filePath = path.join(dir, meta.receiptId + ext);\n" +
                "const binaryData = \$input.first().binary.data;\n" +
                "const buffer = Buffer.from(binaryData.data, 'base64');\n" +
                "fs.writeFileSync(filePath, buffer);\n" +
                "return [{ json: { success: true, filePath } }];")
        })
    }

    // Node 8b: JSON-Datei speichern (Receipt + Line Items)
    val jsonSaveNode = buildJsonObject {
        put("id", jsonSaveId)
        put("name", "JSON speichern")
        put("type", "n8n-nodes-base.code")
        put("typeVersion", 2)
        put("position", buildJsonArray { add(2125); add(150) })
        put("parameters", buildJsonObject {
            put("language", "javaScript")
            put("jsCode",
                "const fs = require('fs');\n" +
                "const path = require('path');\n" +
                "const raw = \$('Ollama OCR').item.json.response;\n" +
                "const meta = \$('Zu Base64').item.json;\n" +
                "const year = new Date().getFullYear();\n" +
                "const dir = '/home/node/.n8n/expenseTracker';\n" +
                "if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });\n" +
                "const ext = meta.mimeType === 'image/png' ? '.png' : '.jpg';\n" +
                "const imagePath = path.join(dir, 'bin', meta.receiptId + ext);\n" +
                "let data = {};\n" +
                "try {\n" +
                "  let jsonStr = raw.trim();\n" +
                "  if (jsonStr.startsWith('\\`\\`\\`')) {\n" +
                "    jsonStr = jsonStr.replace(/^\\`\\`\\`(?:json)?\\n?/, '').replace(/\\n?\\`\\`\\`$/, '');\n" +
                "  }\n" +
                "  data = JSON.parse(jsonStr);\n" +
                "} catch (e) { data = {}; }\n" +
                "const s = data.sender || {};\n" +
                "const items = data.line_items || data.lineitems || [];\n" +
                "const rawNet = data.amountnet ?? data.amount_net ?? null;\n" +
                "const rawVat = data.amountvat ?? data.amount_vat ?? null;\n" +
                "const rawTotal = data.amounttotal ?? data.amount_total ?? null;\n" +
                "const numNet = rawNet !== null ? parseFloat(rawNet) : null;\n" +
                "const numVat = rawVat !== null ? parseFloat(rawVat) : null;\n" +
                "let numTotal = rawTotal !== null ? parseFloat(rawTotal) : null;\n" +
                "if (numNet !== null && numVat !== null && !isNaN(numNet) && !isNaN(numVat)) {\n" +
                "  const calc = Math.round((numNet + numVat) * 100) / 100;\n" +
                "  if (numTotal === null || isNaN(numTotal) || (Math.abs(numTotal - numNet) < 0.01 && numVat > 0)) {\n" +
                "    numTotal = calc;\n" +
                "  }\n" +
                "}\n" +
                "let senderName = s.name || '';\n" +
                "if (!senderName && s.address) {\n" +
                "  const firstLine = s.address.split(/[\\n,]/).map(l => l.trim()).filter(l => l)[0];\n" +
                "  if (firstLine) senderName = firstLine;\n" +
                "}\n" +
                "const receipt = {\n" +
                "  id: meta.receiptId,\n" +
                "  chatId: meta.chatId,\n" +
                "  messageId: meta.messageId,\n" +
                "  createdAt: new Date().toISOString(),\n" +
                "  imagePath,\n" +
                "  documentType: data.documenttype || data.document_type || '',\n" +
                "  language: data.language || '',\n" +
                "  invoiceNumber: data.invoicenumber || data.invoice_number || '',\n" +
                "  invoiceDate: data.invoicedate || data.invoice_date || '',\n" +
                "  dueDate: data.duedate || data.due_date || '',\n" +
                "  senderName: senderName,\n" +
                "  senderAddress: s.address || '',\n" +
                "  senderVatId: s.vatid || s.vat_id || '',\n" +
                "  senderIban: s.iban || '',\n" +
                "  amountNet: isNaN(numNet) ? null : numNet,\n" +
                "  amountVat: isNaN(numVat) ? null : numVat,\n" +
                "  amountTotal: isNaN(numTotal) ? null : numTotal,\n" +
                "  currency: data.currency || 'EUR',\n" +
                "  notes: data.notes || '',\n" +
                "  lineItems: items.map(it => ({\n" +
                "    position: it.position || 0,\n" +
                "    description: it.description || '',\n" +
                "    quantity: it.quantity ?? null,\n" +
                "    unit: it.unit || '',\n" +
                "    unitPriceNet: it.unitpricenet ?? it.unit_price_net ?? null,\n" +
                "    amountNet: it.amountnet ?? it.amount_net ?? null,\n" +
                "    vatRate: it.vatrate ?? it.vat_rate ?? null\n" +
                "  }))\n" +
                "};\n" +
                "const filePath = path.join(dir, 'receipts_' + year + '.jsonl');\n" +
                "fs.appendFileSync(filePath, JSON.stringify(receipt) + '\\n');\n" +
                "return [{ json: { success: true, receiptId: receipt.id, file: filePath } }];")
        })
    }

    // Node 2: Switch – Nachrichten aufteilen (Foto, /export, /list, /delete, /help, default → Agent)
    val commandSwitchNode = buildJsonObject {
        put("id", commandSwitchId)
        put("name", "Nachricht Switch")
        put("type", "n8n-nodes-base.switch")
        put("typeVersion", 3)
        put("position", buildJsonArray { add(500); add(300) })
        put("parameters", buildJsonObject {
            put("mode", "rules")
            put("options", buildJsonObject {
                put("fallbackOutput", "extra")
            })
            put("rules", buildJsonObject {
                put("values", buildJsonArray {
                    add(buildJsonObject {
                        put("conditions", buildJsonObject {
                            put("options", buildJsonObject {
                                put("caseSensitive", true)
                                put("leftValue", "")
                                put("typeValidation", "loose")
                            })
                            put("conditions", buildJsonArray {
                                add(buildJsonObject {
                                    put("id", uuidShort())
                                    put("leftValue", "={{ \$json.message.photo ? 'yes' : '' }}")
                                    put("rightValue", "yes")
                                    put("operator", buildJsonObject {
                                        put("type", "string")
                                        put("operation", "equals")
                                    })
                                })
                            })
                            put("combinator", "and")
                        })
                        put("renameOutput", false)
                        put("outputKey", "0")
                    })
                    add(buildJsonObject {
                        put("conditions", buildJsonObject {
                            put("options", buildJsonObject {
                                put("caseSensitive", true)
                                put("leftValue", "")
                                put("typeValidation", "loose")
                            })
                            put("conditions", buildJsonArray {
                                add(buildJsonObject {
                                    put("id", uuidShort())
                                    put("leftValue", "={{ \$json.message.text }}")
                                    put("rightValue", "/export")
                                    put("operator", buildJsonObject {
                                        put("type", "string")
                                        put("operation", "startsWith")
                                    })
                                })
                            })
                            put("combinator", "and")
                        })
                        put("renameOutput", false)
                        put("outputKey", "1")
                    })
                    add(buildJsonObject {
                        put("conditions", buildJsonObject {
                            put("options", buildJsonObject {
                                put("caseSensitive", true)
                                put("leftValue", "")
                                put("typeValidation", "loose")
                            })
                            put("conditions", buildJsonArray {
                                add(buildJsonObject {
                                    put("id", uuidShort())
                                    put("leftValue", "={{ \$json.message.text }}")
                                    put("rightValue", "/list")
                                    put("operator", buildJsonObject {
                                        put("type", "string")
                                        put("operation", "startsWith")
                                    })
                                })
                            })
                            put("combinator", "and")
                        })
                        put("renameOutput", false)
                        put("outputKey", "2")
                    })
                    add(buildJsonObject {
                        put("conditions", buildJsonObject {
                            put("options", buildJsonObject {
                                put("caseSensitive", true)
                                put("leftValue", "")
                                put("typeValidation", "loose")
                            })
                            put("conditions", buildJsonArray {
                                add(buildJsonObject {
                                    put("id", uuidShort())
                                    put("leftValue", "={{ \$json.message.text }}")
                                    put("rightValue", "/delete")
                                    put("operator", buildJsonObject {
                                        put("type", "string")
                                        put("operation", "startsWith")
                                    })
                                })
                            })
                            put("combinator", "and")
                        })
                        put("renameOutput", false)
                        put("outputKey", "3")
                    })
                    add(buildJsonObject {
                        put("conditions", buildJsonObject {
                            put("options", buildJsonObject {
                                put("caseSensitive", true)
                                put("leftValue", "")
                                put("typeValidation", "loose")
                            })
                            put("conditions", buildJsonArray {
                                add(buildJsonObject {
                                    put("id", uuidShort())
                                    put("leftValue", "={{ \$json.message.text }}")
                                    put("rightValue", "/help")
                                    put("operator", buildJsonObject {
                                        put("type", "string")
                                        put("operation", "startsWith")
                                    })
                                })
                            })
                            put("combinator", "and")
                        })
                        put("renameOutput", false)
                        put("outputKey", "4")
                    })
                })
            })
        })
    }

    // Node 9a: CSV aus JSONL generieren
    val exportCsvNode = buildJsonObject {
        put("id", exportCsvId)
        put("name", "CSV Export")
        put("type", "n8n-nodes-base.code")
        put("typeVersion", 2)
        put("position", buildJsonArray { add(750); add(500) })
        put("parameters", buildJsonObject {
            put("language", "javaScript")
            put("jsCode",
                "const fs = require('fs');\n" +
                "const path = require('path');\n" +
                "const text = \$('Telegram Trigger').item.json.message.text || '';\n" +
                "const parts = text.trim().split(/\\s+/);\n" +
                "const year = parts.length > 1 ? parts[1] : new Date().getFullYear().toString();\n" +
                "const chatId = \$('Telegram Trigger').item.json.message.chat.id;\n" +
                "const dir = '/home/node/.n8n/expenseTracker';\n" +
                "const filePath = path.join(dir, 'receipts_' + year + '.jsonl');\n" +
                "let receipts = [];\n" +
                "if (fs.existsSync(filePath)) {\n" +
                "  const lines = fs.readFileSync(filePath, 'utf8').trim().split('\\n').filter(l => l);\n" +
                "  receipts = lines.map(l => JSON.parse(l)).filter(r => r.chatId === chatId);\n" +
                "}\n" +
                "if (receipts.length === 0) {\n" +
                "  const csv = 'Keine Belege fuer ' + year + ' vorhanden.';\n" +
                "  const b64 = Buffer.from(csv, 'utf8').toString('base64');\n" +
                "  return [{ json: { chatId, year, count: 0, fileName: 'expenses_' + year + '.csv' }, binary: { data: { data: b64, mimeType: 'text/csv', fileName: 'expenses_' + year + '.csv' } } }];\n" +
                "}\n" +
                "function csvEscape(val) {\n" +
                "  const s = String(val ?? '');\n" +
                "  if (s.includes(',') || s.includes('\"') || s.includes('\\n') || s.includes('\\r')) {\n" +
                "    return '\"' + s.replace(/\"/g, '\"\"') + '\"';\n" +
                "  }\n" +
                "  return s;\n" +
                "}\n" +
                "function fmtNum(v) {\n" +
                "  if (v === null || v === undefined) return '';\n" +
                "  const n = parseFloat(v);\n" +
                "  return isNaN(n) ? '' : n.toFixed(2);\n" +
                "}\n" +
                "const headers = ['id','createdAt','documentType','invoiceNumber','invoiceDate','dueDate','senderName','senderAddress','amountNet','amountVat','amountTotal','currency','notes'];\n" +
                "const csvLines = [headers.join(',')];\n" +
                "for (const r of receipts) {\n" +
                "  const net = parseFloat(r.amountNet);\n" +
                "  const vat = parseFloat(r.amountVat);\n" +
                "  let total = parseFloat(r.amountTotal);\n" +
                "  if (!isNaN(net) && !isNaN(vat)) {\n" +
                "    const calc = Math.round((net + vat) * 100) / 100;\n" +
                "    if (isNaN(total) || (Math.abs(total - net) < 0.01 && vat > 0)) total = calc;\n" +
                "  }\n" +
                "  let senderName = r.senderName || '';\n" +
                "  if (!senderName && r.senderAddress) {\n" +
                "    const firstLine = r.senderAddress.split(/[\\n,]/).map(l => l.trim()).filter(l => l)[0];\n" +
                "    if (firstLine) senderName = firstLine;\n" +
                "  }\n" +
                "  const row = {\n" +
                "    id: r.id,\n" +
                "    createdAt: r.createdAt,\n" +
                "    documentType: r.documentType,\n" +
                "    invoiceNumber: r.invoiceNumber,\n" +
                "    invoiceDate: r.invoiceDate,\n" +
                "    dueDate: r.dueDate,\n" +
                "    senderName: senderName,\n" +
                "    senderAddress: r.senderAddress,\n" +
                "    amountNet: fmtNum(r.amountNet),\n" +
                "    amountVat: fmtNum(r.amountVat),\n" +
                "    amountTotal: fmtNum(total),\n" +
                "    currency: r.currency,\n" +
                "    notes: r.notes\n" +
                "  };\n" +
                "  csvLines.push(headers.map(h => csvEscape(row[h])).join(','));\n" +
                "}\n" +
                "const csv = csvLines.join('\\n');\n" +
                "const b64 = Buffer.from(csv, 'utf8').toString('base64');\n" +
                "return [{ json: { chatId, year, count: receipts.length, fileName: 'expenses_' + year + '.csv' }, binary: { data: { data: b64, mimeType: 'text/csv', fileName: 'expenses_' + year + '.csv' } } }];")
        })
    }

    // Node 9b: CSV Datei per Telegram senden
    val sendCsvNode = buildJsonObject {
        put("id", sendCsvId)
        put("name", "CSV senden")
        put("type", "n8n-nodes-base.telegram")
        put("typeVersion", 1.1)
        put("position", buildJsonArray { add(1250); add(450) })
        put("parameters", buildJsonObject {
            put("operation", "sendDocument")
            put("chatId", "={{ \$json.chatId }}")
            put("binaryData", true)
            put("binaryPropertyName", "data")
            put("additionalFields", buildJsonObject {
                put("caption", "={{ '📊 ' + \$json.count + ' Belege für ' + \$json.year }}")
                put("appendAttribution", false)
            })
        })
        put("credentials", buildJsonObject {
            put("telegramApi", buildJsonObject {
                put("id", credentialId)
                put("name", "Telegram Bot")
            })
        })
    }


    // Node 9d: Code – JSONL Dateien auflisten
    val listFilesNode = buildJsonObject {
        put("id", listFilesId)
        put("name", "Dateien auflisten")
        put("type", "n8n-nodes-base.code")
        put("typeVersion", 2)
        put("position", buildJsonArray { add(750); add(600) })
        put("parameters", buildJsonObject {
            put("language", "javaScript")
            put("jsCode",
                "const fs = require('fs');\n" +
                "const path = require('path');\n" +
                "const chatId = \$('Telegram Trigger').item.json.message.chat.id;\n" +
                "const dir = '/home/node/.n8n/expenseTracker';\n" +
                "if (!fs.existsSync(dir)) {\n" +
                "  return [{ json: { chatId, text: 'Keine Belege gespeichert.' } }];\n" +
                "}\n" +
                "const files = fs.readdirSync(dir).filter(f => f.startsWith('receipts_') && f.endsWith('.jsonl')).sort();\n" +
                "if (files.length === 0) {\n" +
                "  return [{ json: { chatId, text: 'Keine Belege gespeichert.' } }];\n" +
                "}\n" +
                "const lines = files.map(f => {\n" +
                "  const year = f.replace('receipts_', '').replace('.jsonl', '');\n" +
                "  const content = fs.readFileSync(path.join(dir, f), 'utf8').trim();\n" +
                "  const count = content ? content.split('\\n').filter(l => l).length : 0;\n" +
                "  return f.replace(/_/g, '\\\\_') + ' - ' + count + ' Belege';\n" +
                "});\n" +
                "return [{ json: { chatId, text: 'Gespeicherte Belege:\\n\\n' + lines.join('\\n') } }];")
        })
    }

    // Node 9e: Telegram – Liste senden
    val listResponseNode = buildJsonObject {
        put("id", listResponseId)
        put("name", "Antwort: Liste")
        put("type", "n8n-nodes-base.telegram")
        put("typeVersion", 1.1)
        put("position", buildJsonArray { add(1000); add(600) })
        put("parameters", buildJsonObject {
            put("operation", "sendMessage")
            put("chatId", "={{ \$('Telegram Trigger').item.json.message.chat.id }}")
            put("text", "={{ \$('Dateien auflisten').item.json.text }}")
            put("additionalFields", buildJsonObject {
                put("reply_to_message_id", "={{ parseInt(\$('Telegram Trigger').item.json.message.message_id) }}")
                put("appendAttribution", false)
            })
        })
        put("credentials", buildJsonObject {
            put("telegramApi", buildJsonObject {
                put("id", credentialId)
                put("name", "Telegram Bot")
            })
        })
    }

    // Node 9g: Code – JSONL Dateien löschen (mit Bestätigung)
    val deleteFilesNode = buildJsonObject {
        put("id", deleteFilesId)
        put("name", "Dateien loeschen")
        put("type", "n8n-nodes-base.code")
        put("typeVersion", 2)
        put("position", buildJsonArray { add(750); add(750) })
        put("parameters", buildJsonObject {
            put("language", "javaScript")
            put("jsCode",
                "const fs = require('fs');\n" +
                "const path = require('path');\n" +
                "const text = \$('Telegram Trigger').item.json.message.text || '';\n" +
                "const parts = text.trim().split(/\\s+/);\n" +
                "const year = parts[1] || '';\n" +
                "const confirm = parts[2] || '';\n" +
                "const chatId = \$('Telegram Trigger').item.json.message.chat.id;\n" +
                "const dir = '/home/node/.n8n/expenseTracker';\n" +
                "if (!year) {\n" +
                "  return [{ json: { chatId, text: 'Usage: /delete <year> oder /delete all' } }];\n" +
                "}\n" +
                "if (confirm !== 'confirm') {\n" +
                "  let filesToDelete = [];\n" +
                "  if (year === 'all') {\n" +
                "    if (fs.existsSync(dir)) {\n" +
                "      filesToDelete = fs.readdirSync(dir).filter(f => f.startsWith('receipts_') && f.endsWith('.jsonl'));\n" +
                "    }\n" +
                "  } else {\n" +
                "    const filePath = path.join(dir, 'receipts_' + year + '.jsonl');\n" +
                "    if (fs.existsSync(filePath)) filesToDelete.push('receipts_' + year + '.jsonl');\n" +
                "  }\n" +
                "  if (filesToDelete.length === 0) {\n" +
                "    return [{ json: { chatId, text: 'Keine Dateien fuer ' + year + ' gefunden.' } }];\n" +
                "  }\n" +
                "  return [{ json: { chatId, text: 'Warnung: Diese Dateien werden geloescht:\\n' + filesToDelete.join('\\n') + '\\n\\nSende /delete ' + year + ' confirm zum Bestaetigen.' } }];\n" +
                "}\n" +
                "let deleted = [];\n" +
                "if (year === 'all') {\n" +
                "  if (fs.existsSync(dir)) {\n" +
                "    const files = fs.readdirSync(dir).filter(f => f.startsWith('receipts_') && f.endsWith('.jsonl'));\n" +
                "    for (const f of files) {\n" +
                "      fs.unlinkSync(path.join(dir, f));\n" +
                "      deleted.push(f);\n" +
                "    }\n" +
                "  }\n" +
                "} else {\n" +
                "  const filePath = path.join(dir, 'receipts_' + year + '.jsonl');\n" +
                "  if (fs.existsSync(filePath)) {\n" +
                "    fs.unlinkSync(filePath);\n" +
                "    deleted.push('receipts_' + year + '.jsonl');\n" +
                "  }\n" +
                "}\n" +
                "return [{ json: { chatId, text: deleted.length ? 'Geloescht:\\n' + deleted.join('\\n') : 'Keine Dateien gefunden.' } }];")
        })
    }

    // Node 9h: Telegram – Lösch-Ergebnis senden
    val deleteResponseNode = buildJsonObject {
        put("id", deleteResponseId)
        put("name", "Antwort: Geloescht")
        put("type", "n8n-nodes-base.telegram")
        put("typeVersion", 1.1)
        put("position", buildJsonArray { add(1000); add(750) })
        put("parameters", buildJsonObject {
            put("operation", "sendMessage")
            put("chatId", "={{ \$('Telegram Trigger').item.json.message.chat.id }}")
            put("text", "={{ \$('Dateien loeschen').item.json.text }}")
            put("additionalFields", buildJsonObject {
                put("reply_to_message_id", "={{ parseInt(\$('Telegram Trigger').item.json.message.message_id) }}")
                put("appendAttribution", false)
            })
        })
        put("credentials", buildJsonObject {
            put("telegramApi", buildJsonObject {
                put("id", credentialId)
                put("name", "Telegram Bot")
            })
        })
    }

    // Node 9i: Code – Hilfe-Text generieren
    val helpNode = buildJsonObject {
        put("id", helpNodeId)
        put("name", "Hilfe generieren")
        put("type", "n8n-nodes-base.code")
        put("typeVersion", 2)
        put("position", buildJsonArray { add(750); add(850) })
        put("parameters", buildJsonObject {
            put("language", "javaScript")
            put("jsCode",
                "const chatId = \$('Telegram Trigger').item.json.message.chat.id;\n" +
                "const text = [\n" +
                "  'Expense Tracker - Verfuegbare Befehle:',\n" +
                "  '',\n" +
                "  'Foto senden',\n" +
                "  '  -> Kassenbon/Rechnung per OCR verarbeiten und speichern',\n" +
                "  '',\n" +
                "  '/export [Jahr]',\n" +
                "  '  -> CSV-Export der Belege (default: aktuelles Jahr)',\n" +
                "  '',\n" +
                "  '/list',\n" +
                "  '  -> Alle gespeicherten Beleg-Dateien anzeigen',\n" +
                "  '',\n" +
                "  '/delete <Jahr>  oder  /delete all',\n" +
                "  '  -> Belege loeschen (erst Vorschau, dann /delete <Jahr> confirm)',\n" +
                "  '',\n" +
                "  'Beliebige Frage',\n" +
                "  '  -> AI-Agent beantwortet Fragen zu deinen Ausgaben'\n" +
                "].join('\\n');\n" +
                "return [{ json: { chatId, text } }];")
        })
    }

    // Node 9j: Telegram – Hilfe senden
    val helpResponseNode = buildJsonObject {
        put("id", helpResponseId)
        put("name", "Antwort: Hilfe")
        put("type", "n8n-nodes-base.telegram")
        put("typeVersion", 1.1)
        put("position", buildJsonArray { add(1000); add(850) })
        put("parameters", buildJsonObject {
            put("operation", "sendMessage")
            put("chatId", "={{ \$('Telegram Trigger').item.json.message.chat.id }}")
            put("text", "={{ \$('Hilfe generieren').item.json.text }}")
            put("additionalFields", buildJsonObject {
                put("reply_to_message_id", "={{ parseInt(\$('Telegram Trigger').item.json.message.message_id) }}")
                put("appendAttribution", false)
            })
        })
        put("credentials", buildJsonObject {
            put("telegramApi", buildJsonObject {
                put("id", credentialId)
                put("name", "Telegram Bot")
            })
        })
    }

    // Node 10: AI Agent – beantwortet Fragen zu Ausgaben
    val aiAgentNode = buildJsonObject {
        put("id", aiAgentId)
        put("name", "AI Agent")
        put("type", "@n8n/n8n-nodes-langchain.agent")
        put("typeVersion", 2)
        put("position", buildJsonArray { add(750); add(700) })
        put("parameters", buildJsonObject {
            put("promptType", "define")
            put("text", "={{ (() => { const msg = \$('Telegram Trigger').item.json.message; const reply = msg.reply_to_message; let prompt = msg.text || ''; if (reply && reply.text) { prompt = '[Referenzierte Nachricht]:\\n' + reply.text + '\\n\\n[Meine Frage]:\\n' + prompt; } return prompt; })() }}")
            put("options", buildJsonObject {
                put("systemMessage", "Du bist ein hilfreicher Ausgaben-Assistent. Du hilfst dem Benutzer, seine Ausgaben zu analysieren. " +
                    "Du kannst nach Belegen suchen, Zusammenfassungen erstellen und Fragen zu gespeicherten Ausgaben beantworten. " +
                    "Antworte immer auf Deutsch und sei präzise. Wenn du keine relevanten Daten findest, sage das ehrlich. " +
                    "Verwende das Tool 'search_expenses' um nach Belegen zu suchen. " +
                    "Wenn der Benutzer auf eine vorherige Nachricht antwortet (Reply), wird diese als '[Referenzierte Nachricht]' mitgeliefert. Beziehe dich darauf in deiner Antwort.")
            })
        })
    }

    // Node 10a: Ollama Chat Model (Sub-Node für AI Agent)
    val ollamaChatNode = buildJsonObject {
        put("id", ollamaChatId)
        put("name", "Ollama Chat Model")
        put("type", "@n8n/n8n-nodes-langchain.lmChatOllama")
        put("typeVersion", 1)
        put("position", buildJsonArray { add(650); add(900) })
        put("parameters", buildJsonObject {
            put("model", agentModel)
            put("options", buildJsonObject {})
        })
        put("credentials", buildJsonObject {
            put("ollamaApi", buildJsonObject {
                put("id", ollamaCredentialId)
                put("name", "Ollama (Auto)")
            })
        })
    }

    // Node 10b: Tool – Ausgaben durchsuchen (Code Tool)
    val agentToolNode = buildJsonObject {
        put("id", agentToolId)
        put("name", "search_expenses")
        put("type", "@n8n/n8n-nodes-langchain.toolCode")
        put("typeVersion", 1.2)
        put("position", buildJsonArray { add(850); add(900) })
        put("parameters", buildJsonObject {
            put("name", "search_expenses")
            put("description", "Durchsucht gespeicherte Kassenbelege und Rechnungen. " +
                "Input ist eine Suchanfrage (z.B. Geschäftsname, Monat, Jahr, Betrag). " +
                "Gibt eine Liste passender Belege mit Datum, Geschäft, Betrag und Artikeln zurück.")
            put("jsCode",
                "const fs = require('fs');\n" +
                "const path = require('path');\n" +
                "const query = \$input.item.json.query || \$input.item.json.chatInput || '';\n" +
                "const dir = '/home/node/.n8n/expenseTracker';\n" +
                "const years = [new Date().getFullYear(), new Date().getFullYear() - 1];\n" +
                "let allReceipts = [];\n" +
                "for (const year of years) {\n" +
                "  const filePath = path.join(dir, 'receipts_' + year + '.jsonl');\n" +
                "  if (fs.existsSync(filePath)) {\n" +
                "    const lines = fs.readFileSync(filePath, 'utf8').trim().split('\\n').filter(l => l);\n" +
                "    allReceipts = allReceipts.concat(lines.map(l => JSON.parse(l)));\n" +
                "  }\n" +
                "}\n" +
                "const q = query.toLowerCase();\n" +
                "let filtered = allReceipts;\n" +
                "if (q) {\n" +
                "  filtered = allReceipts.filter(r => {\n" +
                "    const text = [r.senderName, r.senderAddress, r.invoiceDate, r.notes, \n" +
                "      (r.lineItems || []).map(i => i.description).join(' ')].join(' ').toLowerCase();\n" +
                "    return q.split(' ').some(word => text.includes(word));\n" +
                "  });\n" +
                "}\n" +
                "const results = filtered.slice(0, 10).map(r => ({\n" +
                "  datum: r.invoiceDate || r.createdAt?.substring(0, 10) || 'unbekannt',\n" +
                "  geschaeft: r.senderName || 'unbekannt',\n" +
                "  betrag: r.amountTotal ? r.amountTotal.toFixed(2) + ' ' + (r.currency || 'EUR') : 'unbekannt',\n" +
                "  artikel: (r.lineItems || []).slice(0, 5).map(i => i.description).join(', ')\n" +
                "}));\n" +
                "const summary = 'Gefunden: ' + filtered.length + ' Belege (zeige max. 10).\\n' +\n" +
                "  'Gesamtsumme: ' + filtered.reduce((s, r) => s + (r.amountTotal || 0), 0).toFixed(2) + ' EUR\\n\\n' +\n" +
                "  results.map((r, i) => (i+1) + '. ' + r.datum + ' | ' + r.geschaeft + ' | ' + r.betrag + (r.artikel ? ' (' + r.artikel + ')' : '')).join('\\n');\n" +
                "return summary;")
        })
    }

    // Node 10c: Window Buffer Memory (per chatId)
    val agentMemoryNode = buildJsonObject {
        put("id", agentMemoryId)
        put("name", "Window Buffer Memory")
        put("type", "@n8n/n8n-nodes-langchain.memoryBufferWindow")
        put("typeVersion", 1.2)
        put("position", buildJsonArray { add(1050); add(900) })
        put("parameters", buildJsonObject {
            put("sessionIdType", "customKey")
            put("sessionKey", "={{ \$('Telegram Trigger').item.json.message.chat.id }}")
            put("contextWindowLength", 10)
        })
    }

    // Node 10d: Agent Antwort via Telegram senden
    val agentResponseNode = buildJsonObject {
        put("id", agentResponseId)
        put("name", "Antwort: Agent")
        put("type", "n8n-nodes-base.telegram")
        put("typeVersion", 1.1)
        put("position", buildJsonArray { add(1000); add(700) })
        put("parameters", buildJsonObject {
            put("operation", "sendMessage")
            put("chatId", "={{ \$('Telegram Trigger').item.json.message.chat.id }}")
            put("text", "={{ \$json.output }}")
            put("additionalFields", buildJsonObject {
                put("reply_to_message_id", "={{ parseInt(\$('Telegram Trigger').item.json.message.message_id) }}")
                put("appendAttribution", false)
            })
        })
        put("credentials", buildJsonObject {
            put("telegramApi", buildJsonObject {
                put("id", credentialId)
                put("name", "Telegram Bot")
            })
        })
    }

    // Node 8e: Kein Kassenbon (false branch)
    val noReceiptAnswer = buildJsonObject {
        put("id", noReceiptId)
        put("name", "Antwort: Kein Kassenbon")
        put("type", "n8n-nodes-base.telegram")
        put("typeVersion", 1.1)
        put("position", buildJsonArray { add(1750); add(500) })
        put("parameters", buildJsonObject {
            put("operation", "sendMessage")
            put("chatId", "={{ \$('Telegram Trigger').item.json.message.chat.id }}")
            put("text", "This is not a receipt or invoice. Please send a valid image.")
            put("additionalFields", buildJsonObject {
                put("reply_to_message_id", "={{ parseInt(\$('Telegram Trigger').item.json.message.message_id) }}")
                put("appendAttribution", false)
            })
        })
        put("credentials", buildJsonObject {
            put("telegramApi", buildJsonObject {
                put("id", credentialId)
                put("name", "Telegram Bot")
            })
        })
    }

    val connections = buildJsonObject {
        put("Telegram Trigger", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Nachricht Switch"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("Nachricht Switch", buildJsonObject {
            put("main", buildJsonArray {
                // output 0 = Foto vorhanden → Validating photo
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Antwort: Validating photo"); put("type", "main"); put("index", 0) })
                })
                // output 1 = /export
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "CSV Export"); put("type", "main"); put("index", 0) })
                })
                // output 2 = /list
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Dateien auflisten"); put("type", "main"); put("index", 0) })
                })
                // output 3 = /delete
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Dateien loeschen"); put("type", "main"); put("index", 0) })
                })
                // output 4 = /help
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Hilfe generieren"); put("type", "main"); put("index", 0) })
                })
                // output 5 = default → AI Agent
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "AI Agent"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("CSV Export", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "CSV senden"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("Antwort: Validating photo", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Telegram getFile"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("Telegram getFile", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Bild herunterladen"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("Bild herunterladen", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Zu Base64"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("Zu Base64", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Ollama OCR"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("Ollama OCR", buildJsonObject {
            put("main", buildJsonArray {
                // index 0 = success → IF prüfen
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Ist Kassenbon?"); put("type", "main"); put("index", 0) })
                })
                // index 1 = error → Timeout
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Antwort: Timeout"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("Ist Kassenbon?", buildJsonObject {
            put("main", buildJsonArray {
                // index 0 = true → Format OCR (Telegram) + Restore Binary (Persistenz)
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Format OCR"); put("type", "main"); put("index", 0) })
                    add(buildJsonObject { put("node", "Restore Binary"); put("type", "main"); put("index", 0) })
                })
                // index 1 = false → Kein Kassenbon
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Antwort: Kein Kassenbon"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("Format OCR", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Antwort: OCR Ergebnis"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("Restore Binary", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Bild speichern"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("Bild speichern", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "JSON speichern"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("Dateien auflisten", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Antwort: Liste"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("Dateien loeschen", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Antwort: Geloescht"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("Hilfe generieren", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Antwort: Hilfe"); put("type", "main"); put("index", 0) })
                })
            })
        })
        // AI Agent → Antwort: Agent
        put("AI Agent", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Antwort: Agent"); put("type", "main"); put("index", 0) })
                })
            })
        })
        // Sub-Node Connections (ai_languageModel, ai_tool, ai_memory → AI Agent)
        put("Ollama Chat Model", buildJsonObject {
            put("ai_languageModel", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "AI Agent"); put("type", "ai_languageModel"); put("index", 0) })
                })
            })
        })
        put("search_expenses", buildJsonObject {
            put("ai_tool", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "AI Agent"); put("type", "ai_tool"); put("index", 0) })
                })
            })
        })
        put("Window Buffer Memory", buildJsonObject {
            put("ai_memory", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "AI Agent"); put("type", "ai_memory"); put("index", 0) })
                })
            })
        })
    }

    return WorkflowCreateRequest(
        name = "Expense Tracker",
        nodes = listOf(
            triggerNode,
            validatingNode,
            getFileNode,
            downloadNode,
            toBase64Node,
            timeoutNode,
            ifReceiptNode,
            ocrNode,
            formatNode,
            ocrResultNode,
            restoreBinaryNode,
            saveImageNode,
            jsonSaveNode,
            commandSwitchNode,
            exportCsvNode,
            sendCsvNode,
            listFilesNode,
            listResponseNode,
            deleteFilesNode,
            deleteResponseNode,
            helpNode,
            helpResponseNode,
            noReceiptAnswer,
            aiAgentNode,
            ollamaChatNode,
            agentToolNode,
            agentMemoryNode,
            agentResponseNode
        ),
        connections = connections,
        settings = buildJsonObject {
            put("executionOrder", "v1")
        }
    )
}

fun buildErrorWorkflow(credentialId: String): WorkflowCreateRequest {
    val errorTriggerId = uuidShort()
    val telegramErrorId = uuidShort()

    // Error Trigger Node
    val errorTriggerNode = buildJsonObject {
        put("id", errorTriggerId)
        put("name", "Error Trigger")
        put("type", "n8n-nodes-base.errorTrigger")
        put("typeVersion", 1)
        put("position", buildJsonArray { add(250); add(300) })
        put("parameters", buildJsonObject {})
    }

    // Telegram Error Response Node
    val telegramErrorNode = buildJsonObject {
        put("id", telegramErrorId)
        put("name", "Antwort: Fehler")
        put("type", "n8n-nodes-base.telegram")
        put("typeVersion", 1.1)
        put("position", buildJsonArray { add(500); add(300) })
        put("parameters", buildJsonObject {
            put("operation", "sendMessage")
            put("chatId", "={{ \$json.executionData?.contextData?.nodeParameters?.chatId || \$json.workflow.error.context?.nodeParameters?.chatId || 'unknown' }}")
            put("text", "={{ '⚠️ Fehler: ' + (\$json.lastNode || 'unbekannt') + '\\n\\n' + (\$json.error?.message || \$json.workflow.error?.message || 'Ein Fehler ist aufgetreten') }}")
            put("additionalFields", buildJsonObject {
                put("appendAttribution", false)
            })
        })
        put("credentials", buildJsonObject {
            put("telegramApi", buildJsonObject {
                put("id", credentialId)
                put("name", "Telegram Bot")
            })
        })
    }

    val connections = buildJsonObject {
        put("Error Trigger", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Antwort: Fehler"); put("type", "main"); put("index", 0) })
                })
            })
        })
    }

    return WorkflowCreateRequest(
        name = "Expense Tracker - Error Handler",
        nodes = listOf(errorTriggerNode, telegramErrorNode),
        connections = connections,
        settings = buildJsonObject {
            put("executionOrder", "v1")
        }
    )
}

private fun uuidShort(): String = UUID.randomUUID().toString().replace("-", "").take(8)
