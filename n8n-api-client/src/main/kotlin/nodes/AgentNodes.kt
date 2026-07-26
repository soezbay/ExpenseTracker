package nodes

import kotlinx.serialization.json.*

/**
 * AI Agent and its LangChain sub-nodes (LLM, memory, tools).
 */

internal fun aiAgentNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.aiAgentId)
    put("name", "AI Agent")
    put("type", "@n8n/n8n-nodes-langchain.agent")
    put("typeVersion", 2)
    put("position", buildJsonArray { add(750); add(700) })
    put("parameters", buildJsonObject {
        put("promptType", "define")
        put(
            "text",
            "={{ (() => { const msg = \$('Telegram Trigger').item.json.message; const reply = msg.reply_to_message; let prompt = msg.text || ''; if (reply && reply.text) { prompt = '[Referenced message]:\\n' + reply.text + '\\n\\n[My question]:\\n' + prompt; } return prompt; })() }}"
        )
        put("options", buildJsonObject {
            put(
                "systemMessage",
                "You are a helpful expense assistant. You help the user analyze their expenses. " +
                    "You can search receipts, create summaries and answer questions about saved expenses. " +
                    "Detect the language of the user's latest message and answer in that same language. Be precise. If you cannot find relevant data, say so honestly. " +
                    "Use 'search_expenses' to find individual receipts (merchant, date, item). " +
                    "Use 'get_summary_stats' for sums/averages/min/max in a period (never calculate yourself!). " +
                    "Use 'compare_periods' to compare two years. " +
                    "Use 'top_merchants' for merchants with the highest spending. " +
                    "Use 'get_receipt_by_id' to get all details (all items, tax ID) for a single receipt when you know the ID from a previous 'search_expenses' result. " +
                    "Use 'category_breakdown' for questions about expense categories (e.g. Groceries, Transport, Restaurant). " +
                    "Call at most ONE suitable tool per question. " +
                    "Give exactly ONE final answer. Never repeat yourself and do not rephrase the same content multiple times. " +
                    "Keep your answer short and concise (max 500 words). " +
                    "Do NOT use Markdown formatting (no **, *, _, `). Use '- ' for simple lists. " +
                    "When the user replies to a previous message, it is provided as '[Referenced message]'. Refer to it in your answer."
            )
        })
    })
}

internal fun ollamaChatModelNode(ids: WorkflowIds, ollamaCredentialId: String, agentModel: String) = buildJsonObject {
    put("id", ids.ollamaChatId)
    put("name", "Ollama Chat Model")
    put("type", "@n8n/n8n-nodes-langchain.lmChatOllama")
    put("typeVersion", 1)
    put("position", buildJsonArray { add(650); add(900) })
    put("parameters", buildJsonObject { put("model", agentModel) })
    put("credentials", ollamaCredentials(ollamaCredentialId))
}

internal fun searchExpensesToolNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.agentToolId)
    put("name", "search_expenses")
    put("type", "@n8n/n8n-nodes-langchain.toolCode")
    put("typeVersion", 1.2)
    put("position", buildJsonArray { add(850); add(900) })
    put("parameters", buildJsonObject {
        put("name", "search_expenses")
        put(
            "description",
            "Searches saved receipts and invoices. " +
                "Input is a search query (e.g. merchant name, month, year, amount). " +
                "Returns a list of matching receipts with ID, date, merchant, amount and items. " +
                "The ID can be used with 'get_receipt_by_id' to retrieve all details of a receipt."
        )
        put(
            "jsCode",
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
                "  id: r.id,\n" +
                "  date: r.invoiceDate || r.createdAt?.substring(0, 10) || 'unknown',\n" +
                "  merchant: r.senderName || 'unknown',\n" +
                "  amount: r.amountTotal ? r.amountTotal.toFixed(2) + ' ' + (r.currency || 'EUR') : 'unknown',\n" +
                "  items: (r.lineItems || []).slice(0, 5).map(i => i.description).join(', ')\n" +
                "}));\n" +
                "const summary = 'Found: ' + filtered.length + ' receipts (showing max. 10).\\n' +\n" +
                "  'Total: ' + filtered.reduce((s, r) => s + (r.amountTotal || 0), 0).toFixed(2) + ' EUR\\n\\n' +\n" +
                "  results.map((r, i) => (i+1) + '. [' + r.id + '] ' + r.date + ' | ' + r.merchant + ' | ' + r.amount + (r.items ? ' (' + r.items + ')' : '')).join('\\n');\n" +
                "return summary;"
        )
    })
}

internal fun getSummaryStatsToolNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.statsToolId)
    put("name", "get_summary_stats")
    put("type", "@n8n/n8n-nodes-langchain.toolCode")
    put("typeVersion", 1.2)
    put("position", buildJsonArray { add(850); add(1050) })
    put("parameters", buildJsonObject {
        put("name", "get_summary_stats")
        put(
            "description",
            "Calculates statistics (total, average, count, most/least expensive receipt) for a period. " +
                "Input is optionally a year (e.g. '2025') or 'year-month' (e.g. '2025-03'). Without input the current year is used."
        )
        put(
            "jsCode",
            "const fs = require('fs');\n" +
                "const path = require('path');\n" +
                "const query = (\$input.item.json.query || \$input.item.json.chatInput || '').trim();\n" +
                "const dir = '/home/node/.n8n/expenseTracker';\n" +
                "const yearMatch = query.match(/(\\d{4})/);\n" +
                "const monthMatch = query.match(/(\\d{4})-(\\d{2})/);\n" +
                "const year = yearMatch ? yearMatch[1] : new Date().getFullYear().toString();\n" +
                "const filePath = path.join(dir, 'receipts_' + year + '.jsonl');\n" +
                "let receipts = [];\n" +
                "if (fs.existsSync(filePath)) {\n" +
                "  const lines = fs.readFileSync(filePath, 'utf8').trim().split('\\n').filter(l => l);\n" +
                "  receipts = lines.map(l => JSON.parse(l));\n" +
                "}\n" +
                "if (monthMatch) {\n" +
                "  const prefix = monthMatch[1] + '-' + monthMatch[2];\n" +
                "  receipts = receipts.filter(r => (r.invoiceDate || r.createdAt || '').includes(prefix) || (r.createdAt || '').startsWith(prefix));\n" +
                "}\n" +
                "if (receipts.length === 0) {\n" +
                "  return 'No receipts found for ' + (monthMatch ? monthMatch[0] : year) + '.';\n" +
                "}\n" +
                "const totals = receipts.map(r => r.amountTotal || 0);\n" +
                "const sum = totals.reduce((a, b) => a + b, 0);\n" +
                "const avg = sum / receipts.length;\n" +
                "let maxR = receipts[0], minR = receipts[0];\n" +
                "for (const r of receipts) {\n" +
                "  if ((r.amountTotal || 0) > (maxR.amountTotal || 0)) maxR = r;\n" +
                "  if ((r.amountTotal || 0) < (minR.amountTotal || 0)) minR = r;\n" +
                "}\n" +
                "return 'Period: ' + (monthMatch ? monthMatch[0] : year) + '\\n' +\n" +
                "  'Receipt count: ' + receipts.length + '\\n' +\n" +
                "  'Total: ' + sum.toFixed(2) + ' EUR\\n' +\n" +
                "  'Average per receipt: ' + avg.toFixed(2) + ' EUR\\n' +\n" +
                "  'Most expensive receipt: ' + (maxR.senderName || 'unknown') + ' - ' + (maxR.amountTotal || 0).toFixed(2) + ' EUR\\n' +\n" +
                "  'Least expensive receipt: ' + (minR.senderName || 'unknown') + ' - ' + (minR.amountTotal || 0).toFixed(2) + ' EUR';"
        )
    })
}

internal fun comparePeriodsToolNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.comparePeriodsToolId)
    put("name", "compare_periods")
    put("type", "@n8n/n8n-nodes-langchain.toolCode")
    put("typeVersion", 1.2)
    put("position", buildJsonArray { add(850); add(1200) })
    put("parameters", buildJsonObject {
        put("name", "compare_periods")
        put("description", "Compares spending between two years. Input must contain two 4-digit years, e.g. '2024 2025' or '2024 vs 2025'.")
        put(
            "jsCode",
            "const fs = require('fs');\n" +
                "const path = require('path');\n" +
                "const query = (\$input.item.json.query || \$input.item.json.chatInput || '').trim();\n" +
                "const dir = '/home/node/.n8n/expenseTracker';\n" +
                "const years = [...query.matchAll(/\\d{4}/g)].map(m => m[0]);\n" +
                "if (years.length < 2) {\n" +
                "  return 'Please provide two years, e.g. \\'2024 vs 2025\\'.';\n" +
                "}\n" +
                "const [yearA, yearB] = years;\n" +
                "function loadSum(year) {\n" +
                "  const filePath = path.join(dir, 'receipts_' + year + '.jsonl');\n" +
                "  if (!fs.existsSync(filePath)) return { count: 0, sum: 0 };\n" +
                "  const lines = fs.readFileSync(filePath, 'utf8').trim().split('\\n').filter(l => l);\n" +
                "  const receipts = lines.map(l => JSON.parse(l));\n" +
                "  return { count: receipts.length, sum: receipts.reduce((s, r) => s + (r.amountTotal || 0), 0) };\n" +
                "}\n" +
                "const a = loadSum(yearA);\n" +
                "const b = loadSum(yearB);\n" +
                "const diff = b.sum - a.sum;\n" +
                "const pct = a.sum > 0 ? (diff / a.sum * 100) : 0;\n" +
                "return yearA + ': ' + a.count + ' receipts, ' + a.sum.toFixed(2) + ' EUR\\n' +\n" +
                "  yearB + ': ' + b.count + ' receipts, ' + b.sum.toFixed(2) + ' EUR\\n' +\n" +
                "  'Difference: ' + (diff >= 0 ? '+' : '') + diff.toFixed(2) + ' EUR (' + (pct >= 0 ? '+' : '') + pct.toFixed(1) + '%)';"
        )
    })
}

internal fun topMerchantsToolNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.topMerchantsToolId)
    put("name", "top_merchants")
    put("type", "@n8n/n8n-nodes-langchain.toolCode")
    put("typeVersion", 1.2)
    put("position", buildJsonArray { add(850); add(1350) })
    put("parameters", buildJsonObject {
        put("name", "top_merchants")
        put(
            "description",
            "Shows merchants with the highest total spending or highest receipt count. " +
                "Input is optionally a year (e.g. '2025'). Without input the current year is used."
        )
        put(
            "jsCode",
            "const fs = require('fs');\n" +
                "const path = require('path');\n" +
                "const query = (\$input.item.json.query || \$input.item.json.chatInput || '').trim();\n" +
                "const dir = '/home/node/.n8n/expenseTracker';\n" +
                "const yearMatch = query.match(/(\\d{4})/);\n" +
                "const year = yearMatch ? yearMatch[1] : new Date().getFullYear().toString();\n" +
                "const filePath = path.join(dir, 'receipts_' + year + '.jsonl');\n" +
                "let receipts = [];\n" +
                "if (fs.existsSync(filePath)) {\n" +
                "  const lines = fs.readFileSync(filePath, 'utf8').trim().split('\\n').filter(l => l);\n" +
                "  receipts = lines.map(l => JSON.parse(l));\n" +
                "}\n" +
                "if (receipts.length === 0) {\n" +
                "  return 'No receipts found for ' + year + '.';\n" +
                "}\n" +
                "const byMerchant = {};\n" +
                "for (const r of receipts) {\n" +
                "  const name = r.senderName || 'Unknown';\n" +
                "  if (!byMerchant[name]) byMerchant[name] = { count: 0, sum: 0 };\n" +
                "  byMerchant[name].count++;\n" +
                "  byMerchant[name].sum += (r.amountTotal || 0);\n" +
                "}\n" +
                "const sorted = Object.entries(byMerchant).sort((a, b) => b[1].sum - a[1].sum).slice(0, 5);\n" +
                "return 'Top merchants ' + year + ' (by total spending):\\n' +\n" +
                "  sorted.map(([name, s], i) => (i+1) + '. ' + name + ': ' + s.sum.toFixed(2) + ' EUR (' + s.count + 'x)').join('\\n');"
        )
    })
}

internal fun getReceiptByIdToolNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.receiptByIdToolId)
    put("name", "get_receipt_by_id")
    put("type", "@n8n/n8n-nodes-langchain.toolCode")
    put("typeVersion", 1.2)
    put("position", buildJsonArray { add(850); add(1500) })
    put("parameters", buildJsonObject {
        put("name", "get_receipt_by_id")
        put(
            "description",
            "Returns all details for a single receipt (all items, tax ID, IBAN, notes). " +
                "Input is the receipt ID as shown in square brackets '[...]' in the 'search_expenses' results."
        )
        put(
            "jsCode",
            "const fs = require('fs');\n" +
                "const path = require('path');\n" +
                "const id = (\$input.item.json.query || \$input.item.json.chatInput || '').trim();\n" +
                "const chatId = \$('Telegram Trigger').item.json.message.chat.id;\n" +
                "const dir = '/home/node/.n8n/expenseTracker';\n" +
                "if (!id) {\n" +
                "  return 'Please provide a receipt ID (see search_expenses results).';\n" +
                "}\n" +
                "let found = null;\n" +
                "if (fs.existsSync(dir)) {\n" +
                "  const files = fs.readdirSync(dir).filter(f => f.startsWith('receipts_') && f.endsWith('.jsonl'));\n" +
                "  for (const file of files) {\n" +
                "    const lines = fs.readFileSync(path.join(dir, file), 'utf8').trim().split('\\n').filter(l => l);\n" +
                "    for (const line of lines) {\n" +
                "      const r = JSON.parse(line);\n" +
                "      if (r.id === id && r.chatId === chatId) { found = r; break; }\n" +
                "    }\n" +
                "    if (found) break;\n" +
                "  }\n" +
                "}\n" +
                "if (!found) {\n" +
                "  return 'No receipt with ID \\' ' + id + ' \\' found.';\n" +
                "}\n" +
                "const items = (found.lineItems || []).map((i, idx) => (idx+1) + '. ' + (i.description || 'unknown') + \n" +
                "  (i.quantity ? ' (' + i.quantity + 'x)' : '') + (i.price ? ' - ' + i.price + ' EUR' : '')).join('\\n');\n" +
                "return 'Receipt [' + found.id + ']\\n' +\n" +
                "  'Merchant: ' + (found.senderName || 'unknown') + '\\n' +\n" +
                "  'Address: ' + (found.senderAddress || '-') + '\\n' +\n" +
                "  'Tax ID: ' + (found.senderVatId || '-') + '\\n' +\n" +
                "  'Date: ' + (found.invoiceDate || '-') + '\\n' +\n" +
                "  'Receipt No.: ' + (found.invoiceNumber || '-') + '\\n' +\n" +
                "  'Total: ' + (found.amountTotal || 0).toFixed(2) + ' ' + (found.currency || 'EUR') + '\\n' +\n" +
                "  'VAT: ' + (found.amountVat || 0).toFixed(2) + ' EUR\\n' +\n" +
                "  'Notes: ' + (found.notes || '-') + '\\n\\n' +\n" +
                "  '--- Items ---\\n' + (items || 'No items recorded');"
        )
    })
}

internal fun categoryBreakdownToolNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.categoryBreakdownToolId)
    put("name", "category_breakdown")
    put("type", "@n8n/n8n-nodes-langchain.toolCode")
    put("typeVersion", 1.2)
    put("position", buildJsonArray { add(850); add(1650) })
    put("parameters", buildJsonObject {
        put("name", "category_breakdown")
        put(
            "description",
            "Breaks down expenses of a year by category (e.g. Groceries, Transport, Restaurant, Health, Electronics, Clothing, Leisure, Household, Other). " +
                "Input is optionally a year (e.g. '2025'). Without input the current year is used."
        )
        put(
            "jsCode",
            "const fs = require('fs');\n" +
                "const path = require('path');\n" +
                "const query = (\$input.item.json.query || \$input.item.json.chatInput || '').trim();\n" +
                "const dir = '/home/node/.n8n/expenseTracker';\n" +
                "const yearMatch = query.match(/(\\d{4})/);\n" +
                "const year = yearMatch ? yearMatch[1] : new Date().getFullYear().toString();\n" +
                "const filePath = path.join(dir, 'receipts_' + year + '.jsonl');\n" +
                "let receipts = [];\n" +
                "if (fs.existsSync(filePath)) {\n" +
                "  const lines = fs.readFileSync(filePath, 'utf8').trim().split('\\n').filter(l => l);\n" +
                "  receipts = lines.map(l => JSON.parse(l));\n" +
                "}\n" +
                "if (receipts.length === 0) {\n" +
                "  return 'No receipts found for ' + year + '.';\n" +
                "}\n" +
                "const byCategory = {};\n" +
                "for (const r of receipts) {\n" +
                "  const cat = r.category || 'Other';\n" +
                "  if (!byCategory[cat]) byCategory[cat] = { count: 0, sum: 0 };\n" +
                "  byCategory[cat].count++;\n" +
                "  byCategory[cat].sum += (r.amountTotal || 0);\n" +
                "}\n" +
                "const total = receipts.reduce((s, r) => s + (r.amountTotal || 0), 0);\n" +
                "const sorted = Object.entries(byCategory).sort((a, b) => b[1].sum - a[1].sum);\n" +
                "return 'Expenses by category ' + year + ' (Total: ' + total.toFixed(2) + ' EUR):\\n' +\n" +
                "  sorted.map(([cat, s]) => cat + ': ' + s.sum.toFixed(2) + ' EUR (' + s.count + 'x, ' + (total > 0 ? (s.sum / total * 100).toFixed(1) : '0') + '%)').join('\\n');"
        )
    })
}

internal fun agentMemoryNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.agentMemoryId)
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

internal fun agentResponseNode(ids: WorkflowIds, credentialId: String) = buildJsonObject {
    put("id", ids.agentResponseId)
    put("name", "Reply: Agent")
    put("type", "n8n-nodes-base.telegram")
    put("typeVersion", 1.1)
    put("position", buildJsonArray { add(1000); add(700) })
    put("parameters", buildJsonObject {
        put("operation", "sendMessage")
        put("chatId", "={{ \$('Telegram Trigger').item.json.message.chat.id }}")
        put(
            "text",
            "={{ (() => { let t = \$json.output.replace(/\\*\\*/g, '').replace(/[*_`\\[\\]~]/g, ''); return t.length > 4000 ? t.slice(0, 4000) + '\\n\\n[...shortened]' : t; })() }}"
        )
        put("additionalFields", buildJsonObject {
            put("reply_to_message_id", "={{ parseInt(\$('Telegram Trigger').item.json.message.message_id) }}")
            put("appendAttribution", false)
            put("parseMode", "none")
        })
    })
    put("credentials", telegramCredentials(credentialId))
}
