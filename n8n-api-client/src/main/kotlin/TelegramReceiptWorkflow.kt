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
    val persistPrepId   = uuidShort()
    val saveImageId     = uuidShort()
    val prepareDirId    = uuidShort()
    val readCsvId       = uuidShort()
    val extractCsvId    = uuidShort()
    val mergeCsvId      = uuidShort()
    val convertCsvId    = uuidShort()
    val writeCsvId      = uuidShort()

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
                "return [{ json: { imageBase64: base64 } }];")
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

    // Node 5d: Code – Daten fuer Persistierung aufbereiten (JSON flatten, imagePath)
    val prepPersistNode = buildJsonObject {
        put("id", persistPrepId)
        put("name", "Prep Persist")
        put("type", "n8n-nodes-base.code")
        put("typeVersion", 2)
        put("position", buildJsonArray { add(1900); add(150) })
        put("parameters", buildJsonObject {
            put("language", "javaScript")
            put("jsCode",
                "const raw = \$('Ollama OCR').item.json.response;\n" +
                "let data = {};\n" +
                "try {\n" +
                "  let s = raw.trim();\n" +
                "  if (s.startsWith('```')) s = s.replace(/^```(?:json)?\\n?/, '').replace(/\\n?```$/, '');\n" +
                "  data = JSON.parse(s);\n" +
                "} catch (e) { data = {}; }\n" +
                "const msgId = \$('Telegram Trigger').item.json.message.message_id;\n" +
                "const now = new Date();\n" +
                "const imagePath = '/media/raid_storage/n8n/expenseTracker/images/' + msgId + '.jpg';\n" +
                "const receipt = {\n" +
                "  date: data.invoice_date || data.invoicedate || '',\n" +
                "  sender_name: (data.sender?.name || '').replace(/,/g, ' '),\n" +
                "  sender_address: (data.sender?.address || '').replace(/,/g, ' '),\n" +
                "  invoice_number: data.invoice_number || data.invoicenumber || '',\n" +
                "  amount_total: data.amount_total ?? data.amounttotal ?? '',\n" +
                "  amount_vat: data.amount_vat ?? data.amountvat ?? '',\n" +
                "  currency: data.currency || 'EUR',\n" +
                "  image_path: imagePath,\n" +
                "  created_at: now.toISOString()\n" +
                "};\n" +
                "return [{ json: receipt }];")
        })
    }

    // Node 5e: Write Binary File – Bild lokal speichern (parallel zu Base64)
    val saveImageNode = buildJsonObject {
        put("id", saveImageId)
        put("name", "Bild speichern")
        put("type", "n8n-nodes-base.writeBinaryFile")
        put("typeVersion", 1)
        put("position", buildJsonArray { add(1150); add(450) })
        put("parameters", buildJsonObject {
            put("fileName", "={{ '/media/raid_storage/n8n/expenseTracker/images/' + \$('Telegram Trigger').item.json.message.message_id + '.jpg' }}")
            put("dataPropertyName", "data")
            put("options", buildJsonObject {
                put("executeOnce", true)
            })
        })
    }

    // Node 5f: Execute Command – Verzeichnis und leere CSV anlegen (falls nicht vorhanden)
    val prepareDirNode = buildJsonObject {
        put("id", prepareDirId)
        put("name", "Verzeichnis vorbereiten")
        put("type", "n8n-nodes-base.executeCommand")
        put("typeVersion", 2.1)
        put("position", buildJsonArray { add(1900); add(650) })
        put("parameters", buildJsonObject {
            put("command", "mkdir -p /media/raid_storage/n8n/expenseTracker/images && FILE=\"/media/raid_storage/n8n/expenseTracker/receipts_\$(date +%Y).csv\" && if [ ! -f \"\$FILE\" ]; then echo 'date,sender_name,sender_address,invoice_number,amount_total,amount_vat,currency,image_path,created_at' > \"\$FILE\"; fi && echo 'done'")
            put("executeOnce", true)
        })
    }

    // Node 5g: Read Binary File – bestehende CSV lesen
    val readCsvNode = buildJsonObject {
        put("id", readCsvId)
        put("name", "CSV lesen")
        put("type", "n8n-nodes-base.readBinaryFile")
        put("typeVersion", 1)
        put("position", buildJsonArray { add(1900); add(500) })
        put("parameters", buildJsonObject {
            put("filePath", "={{ '/media/raid_storage/n8n/expenseTracker/receipts_' + new Date().getFullYear() + '.csv' }}")
            put("options", buildJsonObject {
                put("continueOnFail", true)
            })
        })
    }

    // Node 5g: Extract From File – CSV → JSON
    val extractCsvNode = buildJsonObject {
        put("id", extractCsvId)
        put("name", "CSV parsen")
        put("type", "n8n-nodes-base.extractFromFile")
        put("typeVersion", 1)
        put("position", buildJsonArray { add(2150); add(500) })
        put("parameters", buildJsonObject {
            put("options", buildJsonObject {})
            put("operation", "csv")
        })
    }

    // Node 5h: Code – alte + neue Zeilen zusammenfuehren
    val mergeCsvNode = buildJsonObject {
        put("id", mergeCsvId)
        put("name", "CSV zusammenfuehren")
        put("type", "n8n-nodes-base.code")
        put("typeVersion", 2)
        put("position", buildJsonArray { add(2400); add(500) })
        put("parameters", buildJsonObject {
            put("language", "javaScript")
            put("jsCode",
                "const newRow = \$('Prep Persist').item.json;\n" +
                "const existing = \$('CSV parsen').all().map(i => i.json);\n" +
                "if (existing.length === 1 && Object.keys(existing[0]).length === 0) {\n" +
                "  return [{ json: newRow }];\n" +
                "}\n" +
                "const headers = Object.keys(newRow);\n" +
                "const out = existing.filter(r => headers.some(h => r[h] !== undefined && r[h] !== ''));\n" +
                "out.push(newRow);\n" +
                "return out.map(r => ({ json: r }));")
        })
    }

    // Node 5i: Convert to File – JSON → CSV
    val convertCsvNode = buildJsonObject {
        put("id", convertCsvId)
        put("name", "CSV erstellen")
        put("type", "n8n-nodes-base.convertToFile")
        put("typeVersion", 1)
        put("position", buildJsonArray { add(2650); add(500) })
        put("parameters", buildJsonObject {
            put("operation", "csv")
            put("fileName", "={{ 'receipts_' + new Date().getFullYear() + '.csv' }}")
            put("options", buildJsonObject {})
        })
    }

    // Node 5j: Write Binary File – CSV speichern
    val writeCsvNode = buildJsonObject {
        put("id", writeCsvId)
        put("name", "CSV speichern")
        put("type", "n8n-nodes-base.writeBinaryFile")
        put("typeVersion", 1)
        put("position", buildJsonArray { add(2900); add(500) })
        put("parameters", buildJsonObject {
            put("fileName", "={{ '/media/raid_storage/n8n/expenseTracker/receipts_' + new Date().getFullYear() + '.csv' }}")
            put("dataPropertyName", "data")
            put("options", buildJsonObject {
                put("executeOnce", true)
            })
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
        ocrModel.startsWith("glm-ocr")                                        -> "Text Recognition:"
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

    // Node 8: Kein Kassenbon (false branch)
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
                    add(buildJsonObject { put("node", "Bild speichern"); put("type", "main"); put("index", 0) })
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
                // index 0 = true → Format OCR
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Format OCR"); put("type", "main"); put("index", 0) })
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
                    add(buildJsonObject { put("node", "Prep Persist"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("Prep Persist", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "Antwort: OCR Ergebnis"); put("type", "main"); put("index", 0) })
                    add(buildJsonObject { put("node", "Verzeichnis vorbereiten"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("Verzeichnis vorbereiten", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "CSV lesen"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("CSV lesen", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "CSV parsen"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("CSV parsen", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "CSV zusammenfuehren"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("CSV zusammenfuehren", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "CSV erstellen"); put("type", "main"); put("index", 0) })
                })
            })
        })
        put("CSV erstellen", buildJsonObject {
            put("main", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("node", "CSV speichern"); put("type", "main"); put("index", 0) })
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
            noReceiptAnswer,
            // Persistierung
            prepPersistNode,
            saveImageNode,
            prepareDirNode,
            readCsvNode,
            extractCsvNode,
            mergeCsvNode,
            convertCsvNode,
            writeCsvNode
        ),
        connections = connections,
        settings = buildJsonObject {
            put("executionOrder", "v1")
        }
    )
}

private fun uuidShort(): String = UUID.randomUUID().toString().replace("-", "").take(8)
