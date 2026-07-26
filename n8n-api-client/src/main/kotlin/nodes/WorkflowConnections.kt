package nodes

import kotlinx.serialization.json.*

/**
 * All workflow connections, referencing node names defined in the node builders.
 */

internal fun buildWorkflowConnections() = buildJsonObject {
    put("Telegram Trigger", buildJsonObject {
        put("main", buildJsonArray {
            add(buildJsonArray { add(connection("Send Chat Action")) })
        })
    })
    put("Send Chat Action", buildJsonObject {
        put("main", buildJsonArray {
            add(buildJsonArray { add(connection("Message Switch")) })
        })
    })
    put("Message Switch", buildJsonObject {
        put("main", buildJsonArray {
            add(buildJsonArray { add(connection("Reply: Validating Photo")) })
            add(buildJsonArray { add(connection("Receipt Rows")) })
            add(buildJsonArray { add(connection("List Files")) })
            add(buildJsonArray { add(connection("Delete Files")) })
            add(buildJsonArray { add(connection("Generate Help")) })
            add(buildJsonArray { add(connection("AI Agent")) })
        })
    })
    put("Receipt Rows", buildJsonObject {
        put("main", buildJsonArray { add(buildJsonArray { add(connection("XLSX Export")) }) })
    })
    put("XLSX Export", buildJsonObject {
        put("main", buildJsonArray { add(buildJsonArray { add(connection("Send Excel")) }) })
    })
    put("Reply: Validating Photo", buildJsonObject {
        put("main", buildJsonArray { add(buildJsonArray { add(connection("Telegram Get File")) }) })
    })
    put("Telegram Get File", buildJsonObject {
        put("main", buildJsonArray { add(buildJsonArray { add(connection("Download Image")) }) })
    })
    put("Download Image", buildJsonObject {
        put("main", buildJsonArray { add(buildJsonArray { add(connection("To Base64")) }) })
    })
    put("To Base64", buildJsonObject {
        put("main", buildJsonArray { add(buildJsonArray { add(connection("Ollama OCR")) }) })
    })
    put("Ollama OCR", buildJsonObject {
        put("main", buildJsonArray {
            add(buildJsonArray { add(connection("Is Receipt?")) })
            add(buildJsonArray { add(connection("Reply: Timeout")) })
        })
    })
    put("Is Receipt?", buildJsonObject {
        put("main", buildJsonArray {
            add(buildJsonArray {
                add(connection("Format OCR"))
                add(connection("Restore Binary"))
            })
            add(buildJsonArray { add(connection("Reply: Not a Receipt")) })
        })
    })
    put("Format OCR", buildJsonObject {
        put("main", buildJsonArray { add(buildJsonArray { add(connection("Reply: OCR Result")) }) })
    })
    put("Restore Binary", buildJsonObject {
        put("main", buildJsonArray { add(buildJsonArray { add(connection("Save Image")) }) })
    })
    put("Save Image", buildJsonObject {
        put("main", buildJsonArray { add(buildJsonArray { add(connection("Save JSON")) }) })
    })
    put("List Files", buildJsonObject {
        put("main", buildJsonArray { add(buildJsonArray { add(connection("Reply: List")) }) })
    })
    put("Delete Files", buildJsonObject {
        put("main", buildJsonArray { add(buildJsonArray { add(connection("Reply: Deleted")) }) })
    })
    put("Generate Help", buildJsonObject {
        put("main", buildJsonArray { add(buildJsonArray { add(connection("Reply: Help")) }) })
    })
    put("AI Agent", buildJsonObject {
        put("main", buildJsonArray { add(buildJsonArray { add(connection("Reply: Agent")) }) })
    })

    // Sub-node connections
    put("Ollama Chat Model", buildJsonObject {
        put("ai_languageModel", buildJsonArray { add(buildJsonArray { add(connection("AI Agent", "ai_languageModel")) }) })
    })
    put("search_expenses", buildJsonObject {
        put("ai_tool", buildJsonArray { add(buildJsonArray { add(connection("AI Agent", "ai_tool")) }) })
    })
    put("get_summary_stats", buildJsonObject {
        put("ai_tool", buildJsonArray { add(buildJsonArray { add(connection("AI Agent", "ai_tool")) }) })
    })
    put("compare_periods", buildJsonObject {
        put("ai_tool", buildJsonArray { add(buildJsonArray { add(connection("AI Agent", "ai_tool")) }) })
    })
    put("top_merchants", buildJsonObject {
        put("ai_tool", buildJsonArray { add(buildJsonArray { add(connection("AI Agent", "ai_tool")) }) })
    })
    put("get_receipt_by_id", buildJsonObject {
        put("ai_tool", buildJsonArray { add(buildJsonArray { add(connection("AI Agent", "ai_tool")) }) })
    })
    put("category_breakdown", buildJsonObject {
        put("ai_tool", buildJsonArray { add(buildJsonArray { add(connection("AI Agent", "ai_tool")) }) })
    })
    put("Window Buffer Memory", buildJsonObject {
        put("ai_memory", buildJsonArray { add(buildJsonArray { add(connection("AI Agent", "ai_memory")) }) })
    })
}

private fun connection(node: String, type: String = "main") = buildJsonObject {
    put("node", node)
    put("type", type)
    put("index", 0)
}
