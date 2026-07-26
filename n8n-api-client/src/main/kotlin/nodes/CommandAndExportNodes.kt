package nodes

import kotlinx.serialization.json.*

/**
 * Telegram command handlers: /export, /list, /delete and /help.
 */

internal fun exportRowsNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.exportCsvId)
    put("name", "Receipt Rows")
    put("type", "n8n-nodes-base.code")
    put("typeVersion", 2)
    put("position", buildJsonArray { add(750); add(500) })
    put("parameters", buildJsonObject {
        put("language", "javaScript")
        put(
            "jsCode",
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
                "  return [{ json: { hint: 'No receipts found for ' + year + '.' } }];\n" +
                "}\n" +
                "function fmtNum(v) {\n" +
                "  if (v === null || v === undefined) return null;\n" +
                "  const n = parseFloat(v);\n" +
                "  return isNaN(n) ? null : Math.round(n * 100) / 100;\n" +
                "}\n" +
                "return receipts.map(r => {\n" +
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
                "  return { json: {\n" +
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
                "  } };\n" +
                "});"
        )
    })
}

internal fun xlsxExportNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.xlsxExportId)
    put("name", "XLSX Export")
    put("type", "n8n-nodes-base.spreadsheetFile")
    put("typeVersion", 2)
    put("position", buildJsonArray { add(1000); add(500) })
    put("parameters", buildJsonObject {
        put("operation", "toFile")
        put("fileFormat", "xlsx")
        put("binaryPropertyName", "data")
        put("options", buildJsonObject {
            put(
                "fileName",
                "={{ 'expenses_' + ((\$('Telegram Trigger').item.json.message.text || '').trim().split(/\\s+/)[1] || new Date().getFullYear().toString()) + '.xlsx' }}"
            )
            put("sheetName", "Receipts")
        })
    })
}

internal fun sendExcelNode(ids: WorkflowIds, credentialId: String) = buildJsonObject {
    put("id", ids.sendCsvId)
    put("name", "Send Excel")
    put("type", "n8n-nodes-base.telegram")
    put("typeVersion", 1.1)
    put("position", buildJsonArray { add(1250); add(450) })
    put("parameters", buildJsonObject {
        put("operation", "sendDocument")
        put("chatId", "={{ \$('Telegram Trigger').item.json.message.chat.id }}")
        put("binaryData", true)
        put("binaryPropertyName", "data")
        put("additionalFields", buildJsonObject {
            put(
                "caption",
                "={{ '📊 ' + \$('Receipt Rows').all().length + ' receipts for ' + ((\$('Telegram Trigger').item.json.message.text || '').trim().split(/\\s+/)[1] || new Date().getFullYear().toString()) }}"
            )
            put("appendAttribution", false)
        })
    })
    put("credentials", telegramCredentials(credentialId))
}

internal fun listFilesNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.listFilesId)
    put("name", "List Files")
    put("type", "n8n-nodes-base.code")
    put("typeVersion", 2)
    put("position", buildJsonArray { add(750); add(600) })
    put("parameters", buildJsonObject {
        put("language", "javaScript")
        put(
            "jsCode",
            "const fs = require('fs');\n" +
                "const path = require('path');\n" +
                "const chatId = \$('Telegram Trigger').item.json.message.chat.id;\n" +
                "const dir = '/home/node/.n8n/expenseTracker';\n" +
                "if (!fs.existsSync(dir)) {\n" +
                "  return [{ json: { chatId, text: 'No receipts saved.' } }];\n" +
                "}\n" +
                "const files = fs.readdirSync(dir).filter(f => f.startsWith('receipts_') && f.endsWith('.jsonl')).sort();\n" +
                "if (files.length === 0) {\n" +
                "  return [{ json: { chatId, text: 'No receipts saved.' } }];\n" +
                "}\n" +
                "const lines = files.map(f => {\n" +
                "  const year = f.replace('receipts_', '').replace('.jsonl', '');\n" +
                "  const content = fs.readFileSync(path.join(dir, f), 'utf8').trim();\n" +
                "  const count = content ? content.split('\\n').filter(l => l).length : 0;\n" +
                "  return f.replace(/_/g, '\\\\_') + ' - ' + count + ' receipts';\n" +
                "});\n" +
                "return [{ json: { chatId, text: 'Saved receipts:\\n\\n' + lines.join('\\n') } }];"
        )
    })
}

internal fun listResponseNode(ids: WorkflowIds, credentialId: String) = buildJsonObject {
    put("id", ids.listResponseId)
    put("name", "Reply: List")
    put("type", "n8n-nodes-base.telegram")
    put("typeVersion", 1.1)
    put("position", buildJsonArray { add(1000); add(600) })
    put("parameters", buildJsonObject {
        put("operation", "sendMessage")
        put("chatId", "={{ \$('Telegram Trigger').item.json.message.chat.id }}")
        put("text", "={{ \$('List Files').item.json.text }}")
        put("additionalFields", buildJsonObject {
            put("reply_to_message_id", "={{ parseInt(\$('Telegram Trigger').item.json.message.message_id) }}")
            put("appendAttribution", false)
        })
    })
    put("credentials", telegramCredentials(credentialId))
}

internal fun deleteFilesNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.deleteFilesId)
    put("name", "Delete Files")
    put("type", "n8n-nodes-base.code")
    put("typeVersion", 2)
    put("position", buildJsonArray { add(750); add(750) })
    put("parameters", buildJsonObject {
        put("language", "javaScript")
        put(
            "jsCode",
            "const fs = require('fs');\n" +
                "const path = require('path');\n" +
                "const text = \$('Telegram Trigger').item.json.message.text || '';\n" +
                "const parts = text.trim().split(/\\s+/);\n" +
                "const year = parts[1] || '';\n" +
                "const confirm = parts[2] || '';\n" +
                "const chatId = \$('Telegram Trigger').item.json.message.chat.id;\n" +
                "const dir = '/home/node/.n8n/expenseTracker';\n" +
                "if (!year) {\n" +
                "  return [{ json: { chatId, text: 'Usage: /delete <year> or /delete all' } }];\n" +
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
                "    return [{ json: { chatId, text: 'No files found for ' + year + '.' } }];\n" +
                "  }\n" +
                "  return [{ json: { chatId, text: 'Warning: These files will be deleted:\\n' + filesToDelete.join('\\n') + '\\n\\nSend /delete ' + year + ' confirm to confirm.' } }];\n" +
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
                "return [{ json: { chatId, text: deleted.length ? 'Deleted:\\n' + deleted.join('\\n') : 'No files found.' } }];"
        )
    })
}

internal fun deleteResponseNode(ids: WorkflowIds, credentialId: String) = buildJsonObject {
    put("id", ids.deleteResponseId)
    put("name", "Reply: Deleted")
    put("type", "n8n-nodes-base.telegram")
    put("typeVersion", 1.1)
    put("position", buildJsonArray { add(1000); add(750) })
    put("parameters", buildJsonObject {
        put("operation", "sendMessage")
        put("chatId", "={{ \$('Telegram Trigger').item.json.message.chat.id }}")
        put("text", "={{ \$('Delete Files').item.json.text }}")
        put("additionalFields", buildJsonObject {
            put("reply_to_message_id", "={{ parseInt(\$('Telegram Trigger').item.json.message.message_id) }}")
            put("appendAttribution", false)
        })
    })
    put("credentials", telegramCredentials(credentialId))
}

internal fun helpGeneratorNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.helpNodeId)
    put("name", "Generate Help")
    put("type", "n8n-nodes-base.code")
    put("typeVersion", 2)
    put("position", buildJsonArray { add(750); add(850) })
    put("parameters", buildJsonObject {
        put("language", "javaScript")
        put(
            "jsCode",
            "const chatId = \$('Telegram Trigger').item.json.message.chat.id;\n" +
                "const text = [\n" +
                "  'Expense Tracker - Available commands:',\n" +
                "  '',\n" +
                "  'Send a photo',\n" +
                "  '  -> Process and save a receipt/invoice via OCR',\n" +
                "  '',\n" +
                "  '/export [year]',\n" +
                "  '  -> Excel export of receipts (default: current year)',\n" +
                "  '',\n" +
                "  '/list',\n" +
                "  '  -> Show all saved receipt files',\n" +
                "  '',\n" +
                "  '/delete <year>  or  /delete all',\n" +
                "  '  -> Delete receipts (preview first, then /delete <year> confirm)',\n" +
                "  '',\n" +
                "  'Any question',\n" +
                "  '  -> AI agent answers questions about your expenses'\n" +
                "].join('\\n');\n" +
                "return [{ json: { chatId, text } }];"
        )
    })
}

internal fun helpResponseNode(ids: WorkflowIds, credentialId: String) = buildJsonObject {
    put("id", ids.helpResponseId)
    put("name", "Reply: Help")
    put("type", "n8n-nodes-base.telegram")
    put("typeVersion", 1.1)
    put("position", buildJsonArray { add(1000); add(850) })
    put("parameters", buildJsonObject {
        put("operation", "sendMessage")
        put("chatId", "={{ \$('Telegram Trigger').item.json.message.chat.id }}")
        put("text", "={{ \$('Generate Help').item.json.text }}")
        put("additionalFields", buildJsonObject {
            put("reply_to_message_id", "={{ parseInt(\$('Telegram Trigger').item.json.message.message_id) }}")
            put("appendAttribution", false)
        })
    })
    put("credentials", telegramCredentials(credentialId))
}
