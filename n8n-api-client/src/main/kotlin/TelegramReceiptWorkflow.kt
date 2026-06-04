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
    val ollamaModel = dotenv["OLLAMA_MODEL"] ?: System.getenv("OLLAMA_MODEL") ?: "moondream2"
    val ocrModel = dotenv["OLLAMA_OCR_MODEL"] ?: System.getenv("OLLAMA_OCR_MODEL") ?: "deepseek-ocr:latest"
    // =====================

    val client = N8nClient(baseUrl = n8nUrl, apiKey = apiKey)

    runBlocking {
        try {
            println("Erstelle Telegram Credential...")
            val credentialId = client.findOrCreateTelegramCredential(botToken)
            println("✅ Credential erstellt: $credentialId")

            val workflowName = "Expense Tracker"
            println("Prüfe auf vorhandenen Workflow '$workflowName'...")
            val existing = client.listWorkflows().find { it.name == workflowName }
            if (existing != null) {
                println("Gefunden [${existing.id}]. Lösche alten Workflow...")
                client.deleteWorkflow(existing.id!!)
                println("✅ Alten Workflow gelöscht.")
            }

            println("Erstelle neuen Workflow...")
            val workflow = buildReceiptValidationWorkflow(credentialId, ollamaUrl, ollamaModel, ocrModel, botToken)
            val created = client.createFullWorkflow(workflow)
            println("✅ Workflow erstellt: [${created.id}] ${created.name}")

            println("""

                📋 Workflow erstellt (INAKTIV).
                Zum Testen in n8n UI:
                1. Workflow öffnen
                2. Auf "Execute Workflow" klicken
                3. Bild an Telegram Bot senden
                4. Nach erfolgreichem Test: Workflow aktivieren (Toggle oben rechts)
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

fun buildReceiptValidationWorkflow(credentialId: String, ollamaUrl: String, model: String, ocrModel: String, botToken: String): WorkflowCreateRequest {
    val triggerId       = uuidShort()
    val ifPhotoId       = uuidShort()
    val validatingId    = uuidShort()
    val getFileId       = uuidShort()
    val downloadId      = uuidShort()
    val toBase64Id      = uuidShort()
    val ifReceiptId     = uuidShort()
    val noReceiptId     = uuidShort()
    val noPhotoId       = uuidShort()
    val timeoutId       = uuidShort()
    val ocrId           = uuidShort()
    val formatId        = uuidShort()
    val ocrResultId     = uuidShort()
    val saveImageId     = uuidShort()
    val restoreBinaryId = uuidShort()
    val jsonSaveId      = uuidShort()

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

    // Node 2: IF – Foto vorhanden? (true=index 0 → Bild vorhanden, false=index 1 → kein Bild)
    val ifPhotoNode = buildJsonObject {
        put("id", ifPhotoId)
        put("name", "Foto vorhanden?")
        put("type", "n8n-nodes-base.if")
        put("typeVersion", 1)
        put("position", buildJsonArray { add(500); add(300) })
        put("parameters", buildJsonObject {
            put("conditions", buildJsonObject {
                put("string", buildJsonArray {
                    add(buildJsonObject {
                        put("value1", "={{ \$json.message.photo ? \$json.message.photo.length.toString() : '' }}")
                        put("operation", "isNotEmpty")
                    })
                })
            })
        })
    }

    // Node 3: Validating photo (true branch nach Foto vorhanden?)
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

    // Node 4: Kein Bild → Telegram Antwort (false branch)
    val noPhotoNode = buildJsonObject {
        put("id", noPhotoId)
        put("name", "Antwort: Kein Bild")
        put("type", "n8n-nodes-base.telegram")
        put("typeVersion", 1.1)
        put("position", buildJsonArray { add(750); add(500) })
        put("parameters", buildJsonObject {
            put("operation", "sendMessage")
            put("chatId", "={{ \$('Telegram Trigger').item.json.message.chat.id }}")
            put("text", "Bitte sende ein Bild von deinem Kassenbon oder deiner Rechnung.")
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
                "  senderName: s.name || '',\n" +
                "  senderAddress: s.address || '',\n" +
                "  senderVatId: s.vatid || s.vat_id || '',\n" +
                "  senderIban: s.iban || '',\n" +
                "  amountNet: data.amountnet ?? data.amount_net ?? null,\n" +
                "  amountVat: data.amountvat ?? data.amount_vat ?? null,\n" +
                "  amountTotal: data.amounttotal ?? data.amount_total ?? null,\n" +
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
                    add(buildJsonObject { put("node", "Foto vorhanden?"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("Foto vorhanden?", buildJsonObject {
            put("main", buildJsonArray {
                // index 0 = true → Foto vorhanden → validating photo
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Antwort: Validating photo"); put("type", "main"); put("index", 0) })
                })
                // index 1 = false → kein Foto
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Antwort: Kein Bild"); put("type", "main"); put("index", 0) })
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
    }

    return WorkflowCreateRequest(
        name = "Expense Tracker",
        nodes = listOf(
            triggerNode,
            ifPhotoNode,
            validatingNode,
            noPhotoNode,
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
            noReceiptAnswer
        ),
        connections = connections,
        settings = buildJsonObject {
            put("executionOrder", "v1")
        }
    )
}

private fun uuidShort(): String = UUID.randomUUID().toString().replace("-", "").take(8)
