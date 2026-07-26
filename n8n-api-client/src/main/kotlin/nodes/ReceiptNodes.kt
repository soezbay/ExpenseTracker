package nodes

import kotlinx.serialization.json.*

/**
 * Receipt processing pipeline: download, OCR, validation, persistence and replies.
 */

internal fun validatingNode(ids: WorkflowIds, credentialId: String) = buildJsonObject {
    put("id", ids.validatingId)
    put("name", "Reply: Validating Photo")
    put("type", "n8n-nodes-base.telegram")
    put("typeVersion", 1.1)
    put("position", buildJsonArray { add(750); add(300) })
    put("parameters", buildJsonObject {
        put("operation", "sendMessage")
        put("chatId", "={{ \$('Telegram Trigger').item.json.message.chat.id }}")
        put("text", "Validating photo… please wait.")
        put("additionalFields", buildJsonObject {
            put("reply_to_message_id", "={{ parseInt(\$('Telegram Trigger').item.json.message.message_id) }}")
            put("appendAttribution", false)
        })
    })
    put("credentials", telegramCredentials(credentialId))
}

internal fun getFileNode(ids: WorkflowIds, botToken: String) = buildJsonObject {
    put("id", ids.getFileId)
    put("name", "Telegram Get File")
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
                    put(
                        "value",
                        "={{ \$('Telegram Trigger').item.json.message.photo[\$('Telegram Trigger').item.json.message.photo.length - 1].file_id }}"
                    )
                })
            })
        })
    })
}

internal fun downloadImageNode(ids: WorkflowIds, botToken: String) = buildJsonObject {
    put("id", ids.downloadId)
    put("name", "Download Image")
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

internal fun toBase64Node(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.toBase64Id)
    put("name", "To Base64")
    put("type", "n8n-nodes-base.code")
    put("typeVersion", 2)
    put("position", buildJsonArray { add(1125); add(300) })
    put("parameters", buildJsonObject {
        put("language", "javaScript")
        put(
            "jsCode",
            "const items = \$input.all();\n" +
                "const item = items[0];\n" +
                "const binaryKey = Object.keys(item.binary)[0];\n" +
                "const binaryData = item.binary[binaryKey];\n" +
                "const buffer = await this.helpers.getBinaryDataBuffer(0, binaryKey);\n" +
                "const base64 = buffer.toString('base64');\n" +
                "const trigger = \$('Telegram Trigger').item.json.message;\n" +
                "const chatId = trigger.chat.id;\n" +
                "const messageId = trigger.message_id;\n" +
                "const receiptId = chatId + '_' + messageId + '_' + Math.random().toString(36).substring(2, 8);\n" +
                "return [{ json: { imageBase64: base64, chatId, messageId, receiptId, mimeType: binaryData.mimeType || 'image/jpeg' } }];"
        )
    })
}

internal fun timeoutNode(ids: WorkflowIds, credentialId: String) = buildJsonObject {
    put("id", ids.timeoutId)
    put("name", "Reply: Timeout")
    put("type", "n8n-nodes-base.telegram")
    put("typeVersion", 1.1)
    put("position", buildJsonArray { add(1500); add(600) })
    put("parameters", buildJsonObject {
        put("operation", "sendMessage")
        put("chatId", "={{ \$('Telegram Trigger').item.json.message.chat.id }}")
        put("text", "Processing took too long. Please try again.")
        put("additionalFields", buildJsonObject {
            put("reply_to_message_id", "={{ parseInt(\$('Telegram Trigger').item.json.message.message_id) }}")
            put("appendAttribution", false)
        })
    })
    put("credentials", telegramCredentials(credentialId))
}

internal fun formatOcrNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.formatId)
    put("name", "Format OCR")
    put("type", "n8n-nodes-base.code")
    put("typeVersion", 2)
    put("position", buildJsonArray { add(1750); add(150) })
    put("parameters", buildJsonObject {
        put("language", "javaScript")
        put(
            "jsCode",
            "const raw = \$input.first().json.response;\n" +
                "let formatted;\n" +
                "try {\n" +
                "  let jsonStr = raw.trim();\n" +
                "  if (jsonStr.startsWith('\\`\\`\\`')) {\n" +
                "    jsonStr = jsonStr.replace(/^\\`\\`\\`(?:json)?\\n?/, '').replace(/\\n?\\`\\`\\`\$/, '');\n" +
                "  }\n" +
                "  const data = JSON.parse(jsonStr);\n" +
                "  const lines = [];\n" +
                "  if (data.sender?.name) lines.push(data.sender.name);\n" +
                "  if (data.sender?.address) lines.push(data.sender.address);\n" +
                "  const vatId = data.sender?.vat_id || data.sender?.vatid || '';\n" +
                "  if (vatId) lines.push('Tax ID: ' + vatId);\n" +
                "  const date = data.invoice_date || data.invoicedate;\n" +
                "  if (date) lines.push('Date: ' + date);\n" +
                "  const nr = data.invoice_number || data.invoicenumber;\n" +
                "  if (nr) lines.push('Receipt No.: ' + nr);\n" +
                "  lines.push('');\n" +
                "  lines.push('--- Items ---');\n" +
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
                "  if (total !== null && total !== undefined) lines.push('Total: ' + total.toFixed(2) + ' ' + (data.currency || 'EUR'));\n" +
                "  const vat = data.amount_vat ?? data.amountvat;\n" +
                "  if (vat) lines.push('VAT: ' + vat.toFixed(2) + ' ' + (data.currency || 'EUR'));\n" +
                "  formatted = lines.join('\\n');\n" +
                "} catch (e) {\n" +
                "  formatted = raw;\n" +
                "}\n" +
                "return [{ json: { response: formatted } }];"
        )
    })
}

internal fun ifReceiptNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.ifReceiptId)
    put("name", "Is Receipt?")
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

internal fun ocrNode(ids: WorkflowIds, ollamaUrl: String, ocrModel: String) = buildJsonObject {
    val ocrPrompt = when {
        ocrModel.startsWith("Keyvan/german-ocr") || ocrModel.startsWith("german-ocr") ->
            "Extrahiere die Rechnung im Bild als JSON."
        ocrModel.startsWith("deepseek-ocr") ->
            "Extract the text in the image."
        else ->
            "Extract the text in the image."
    }
    put("id", ids.ocrId)
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
        put("options", buildJsonObject { put("timeout", 300000) })
        put("onError", "continueErrorOutput")
    })
}

internal fun ocrResultNode(ids: WorkflowIds, credentialId: String) = buildJsonObject {
    put("id", ids.ocrResultId)
    put("name", "Reply: OCR Result")
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
    put("credentials", telegramCredentials(credentialId))
}

internal fun restoreBinaryNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.restoreBinaryId)
    put("name", "Restore Binary")
    put("type", "n8n-nodes-base.code")
    put("typeVersion", 2)
    put("position", buildJsonArray { add(1875); add(300) })
    put("parameters", buildJsonObject {
        put("language", "javaScript")
        put(
            "jsCode",
            "const meta = \$('To Base64').item.json;\n" +
                "const binaryData = \$('Download Image').item.binary;\n" +
                "const key = Object.keys(binaryData)[0];\n" +
                "return [{ json: { receiptId: meta.receiptId, mimeType: meta.mimeType }, binary: { data: binaryData[key] } }];"
        )
    })
}

internal fun saveImageNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.saveImageId)
    put("name", "Save Image")
    put("type", "n8n-nodes-base.code")
    put("typeVersion", 2)
    put("position", buildJsonArray { add(2000); add(300) })
    put("parameters", buildJsonObject {
        put("language", "javaScript")
        put(
            "jsCode",
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
                "return [{ json: { success: true, filePath } }];"
        )
    })
}

internal fun saveJsonNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.jsonSaveId)
    put("name", "Save JSON")
    put("type", "n8n-nodes-base.code")
    put("typeVersion", 2)
    put("position", buildJsonArray { add(2125); add(150) })
    put("parameters", buildJsonObject {
        put("language", "javaScript")
        put(
            "jsCode",
            "const fs = require('fs');\n" +
                "const path = require('path');\n" +
                "const raw = \$('Ollama OCR').item.json.response;\n" +
                "const meta = \$('To Base64').item.json;\n" +
                "const year = new Date().getFullYear();\n" +
                "const dir = '/home/node/.n8n/expenseTracker';\n" +
                "if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });\n" +
                "const ext = meta.mimeType === 'image/png' ? '.png' : '.jpg';\n" +
                "const imagePath = path.join(dir, 'bin', meta.receiptId + ext);\n" +
                "let data = {};\n" +
                "try {\n" +
                "  let jsonStr = raw.trim();\n" +
                "  if (jsonStr.startsWith('\\`\\`\\`')) {\n" +
                "    jsonStr = jsonStr.replace(/^\\`\\`\\`(?:json)?\\n?/, '').replace(/\\n?\\`\\`\\`\$/, '');\n" +
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
                "const CATEGORY_RULES = [\n" +
                "  ['Groceries', ['rewe', 'edeka', 'aldi', 'lidl', 'netto', 'kaufland', 'penny', 'real', 'marktkauf', 'norma', 'famila', 'combi', 'supermarkt', 'bio company']],\n" +
                "  ['Restaurant', ['restaurant', 'pizzeria', 'imbiss', 'cafe', 'café', 'bäckerei', 'baeckerei', 'mcdonald', 'burger king', 'kfc', 'subway', 'döner', 'doener']],\n" +
                "  ['Transport', ['tankstelle', 'aral', 'shell', 'esso', 'total', 'bft', 'db bahn', 'deutsche bahn', 'flixbus', 'uber', 'taxi', 'parkhaus', 'parken', 'bvg', 'vrr', 'vvo']],\n" +
                "  ['Health', ['apotheke', 'arzt', 'praxis', 'zahnarzt', 'krankenhaus', 'dm-drogerie', 'rossmann', 'physio']],\n" +
                "  ['Electronics', ['media markt', 'mediamarkt', 'saturn', 'expert', 'euronics', 'amazon', 'conrad']],\n" +
                "  ['Clothing', ['h&m', 'zara', 'c&a', 'primark', 'esprit', 'zalando', 'about you', 'deichmann']],\n" +
                "  ['Leisure', ['kino', 'cinema', 'fitness', 'gym', 'schwimmbad', 'therme', 'museum', 'theater']],\n" +
                "  ['Household', ['ikea', 'obi', 'hornbach', 'bauhaus', 'toom', 'poco', 'roller']]\n" +
                "];\n" +
                "function guessCategory(name, itemDescriptions) {\n" +
                "  const text = (name + ' ' + itemDescriptions.join(' ')).toLowerCase();\n" +
                "  for (const [category, keywords] of CATEGORY_RULES) {\n" +
                "    if (keywords.some(kw => text.includes(kw))) return category;\n" +
                "  }\n" +
                "  return 'Other';\n" +
                "}\n" +
                "const category = guessCategory(senderName, items.map(it => it.description || ''));\n" +
                "const receipt = {\n" +
                "  id: meta.receiptId,\n" +
                "  chatId: meta.chatId,\n" +
                "  messageId: meta.messageId,\n" +
                "  createdAt: new Date().toISOString(),\n" +
                "  imagePath,\n" +
                "  documentType: data.documenttype || data.document_type || '',\n" +
                "  category,\n" +
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
                "return [{ json: { success: true, receiptId: receipt.id, file: filePath } }];"
        )
    })
}

internal fun noReceiptNode(ids: WorkflowIds, credentialId: String) = buildJsonObject {
    put("id", ids.noReceiptId)
    put("name", "Reply: Not a Receipt")
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
    put("credentials", telegramCredentials(credentialId))
}
