package nodes

import kotlinx.serialization.json.*

/**
 * Nodes shared by the whole workflow: trigger, typing indicator and command switch.
 */

internal fun telegramTriggerNode(ids: WorkflowIds, credentialId: String) = buildJsonObject {
    put("id", ids.triggerId)
    put("name", "Telegram Trigger")
    put("type", "n8n-nodes-base.telegramTrigger")
    put("typeVersion", 1.1)
    put("position", buildJsonArray { add(250); add(300) })
    put("webhookId", ids.triggerId)
    put("parameters", buildJsonObject {
        put("updates", buildJsonArray { add("message") })
        put("additionalFields", buildJsonObject {})
    })
    put("credentials", telegramCredentials(credentialId))
}

internal fun sendChatActionNode(ids: WorkflowIds, botToken: String) = buildJsonObject {
    put("id", ids.sendChatActionId)
    put("name", "Send Chat Action")
    put("type", "n8n-nodes-base.httpRequest")
    put("typeVersion", 4.2)
    put("position", buildJsonArray { add(375); add(300) })
    put("parameters", buildJsonObject {
        put("method", "POST")
        put("url", "https://api.telegram.org/bot$botToken/sendChatAction")
        put("sendBody", true)
        put("specifyBody", "json")
        put(
            "jsonBody",
            "={{ JSON.stringify({ chat_id: \$('Telegram Trigger').item.json.message.chat.id, action: 'typing' }) }}"
        )
        put("options", buildJsonObject {})
    })
}

internal fun commandSwitchNode(ids: WorkflowIds) = buildJsonObject {
    put("id", ids.commandSwitchId)
    put("name", "Message Switch")
    put("type", "n8n-nodes-base.switch")
    put("typeVersion", 3)
    put("position", buildJsonArray { add(500); add(300) })
    put("parameters", buildJsonObject {
        put("mode", "rules")
        put("options", buildJsonObject { put("fallbackOutput", "extra") })
        put("rules", buildJsonObject {
            put("values", buildJsonArray {
                add(switchRule(ids, "0", "photo", "={{ \$('Telegram Trigger').item.json.message.photo ? 'yes' : '' }}", "yes"))
                add(switchRule(ids, "1", "/export", "={{ \$('Telegram Trigger').item.json.message.text }}", "/export"))
                add(switchRule(ids, "2", "/list", "={{ \$('Telegram Trigger').item.json.message.text }}", "/list"))
                add(switchRule(ids, "3", "/delete", "={{ \$('Telegram Trigger').item.json.message.text }}", "/delete"))
                add(switchRule(ids, "4", "/help", "={{ \$('Telegram Trigger').item.json.message.text }}", "/help"))
            })
        })
    })
}

private fun switchRule(ids: WorkflowIds, outputKey: String, command: String, leftValue: String, rightValue: String) = buildJsonObject {
    put("conditions", buildJsonObject {
        put("options", buildJsonObject {
            put("caseSensitive", true)
            put("leftValue", "")
            put("typeValidation", "loose")
        })
        put("conditions", buildJsonArray {
            add(buildJsonObject {
                put("id", uuidShort())
                put("leftValue", leftValue)
                put("rightValue", rightValue)
                put("operator", buildJsonObject {
                    put("type", "string")
                    put("operation", if (command == "photo") "equals" else "startsWith")
                })
            })
        })
        put("combinator", "and")
    })
    put("renameOutput", false)
    put("outputKey", outputKey)
}

internal fun telegramCredentials(credentialId: String) = buildJsonObject {
    put("telegramApi", buildJsonObject {
        put("id", credentialId)
        put("name", "Telegram Bot")
    })
}

internal fun ollamaCredentials(ollamaCredentialId: String) = buildJsonObject {
    put("ollamaApi", buildJsonObject {
        put("id", ollamaCredentialId)
        put("name", "Ollama (Auto)")
    })
}
