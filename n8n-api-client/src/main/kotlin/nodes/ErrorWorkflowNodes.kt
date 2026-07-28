package nodes

import kotlinx.serialization.json.*
import java.util.*

/**
 * Error handler workflow nodes.
 */

internal fun errorTriggerNode() = buildJsonObject {
    put("id", UUID.randomUUID().toString().replace("-", "").take(8))
    put("name", "Error Trigger")
    put("type", "n8n-nodes-base.errorTrigger")
    put("typeVersion", 1)
    put("position", buildJsonArray { add(250); add(300) })
    put("parameters", buildJsonObject {})
}

internal fun errorResponseNode(credentialId: String) = buildJsonObject {
    put("id", UUID.randomUUID().toString().replace("-", "").take(8))
    put("name", "Reply: Error")
    put("type", "n8n-nodes-base.telegram")
    put("typeVersion", 1.1)
    put("position", buildJsonArray { add(500); add(300) })
    put("parameters", buildJsonObject {
        put("operation", "sendMessage")
        put("chatId", "={{ \$json.execution?.error?.node?.parameters?.chatId || 'unknown' }}")
        put("text", "={{ '⚠️ Error: ' + (\$json.execution?.error?.message || 'An error occurred') + '\\n\\nNode: ' + (\$json.execution?.lastNodeExecuted || 'unknown') + '\\nWorkflow: ' + (\$json.workflow?.name || 'unknown') }}")
        put("additionalFields", buildJsonObject { put("appendAttribution", false) })
    })
    put("credentials", telegramCredentials(credentialId))
}

internal fun buildErrorConnections() = buildJsonObject {
    put("Error Trigger", buildJsonObject {
        put("main", buildJsonArray {
            add(buildJsonArray {
                add(buildJsonObject {
                    put("node", "Reply: Error")
                    put("type", "main")
                    put("index", 0)
                })
            })
        })
    })
}
