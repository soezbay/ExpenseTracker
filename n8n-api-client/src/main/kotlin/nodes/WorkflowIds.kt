package nodes

import java.util.*

/**
 * Holds all node IDs for the Expense Tracker workflow.
 * Keeps the main builder clean and ensures IDs are generated once.
 */
internal fun uuidShort(): String = UUID.randomUUID().toString().replace("-", "").take(8)

internal data class WorkflowIds(
    val triggerId: String = uuidShort(),
    val sendChatActionId: String = uuidShort(),
    val validatingId: String = uuidShort(),
    val getFileId: String = uuidShort(),
    val downloadId: String = uuidShort(),
    val toBase64Id: String = uuidShort(),
    val ifReceiptId: String = uuidShort(),
    val noReceiptId: String = uuidShort(),
    val timeoutId: String = uuidShort(),
    val ocrId: String = uuidShort(),
    val formatId: String = uuidShort(),
    val ocrResultId: String = uuidShort(),
    val saveImageId: String = uuidShort(),
    val restoreBinaryId: String = uuidShort(),
    val jsonSaveId: String = uuidShort(),
    val commandSwitchId: String = uuidShort(),
    val exportCsvId: String = uuidShort(),
    val xlsxExportId: String = uuidShort(),
    val sendCsvId: String = uuidShort(),
    val aiAgentId: String = uuidShort(),
    val ollamaChatId: String = uuidShort(),
    val agentToolId: String = uuidShort(),
    val statsToolId: String = uuidShort(),
    val comparePeriodsToolId: String = uuidShort(),
    val topMerchantsToolId: String = uuidShort(),
    val receiptByIdToolId: String = uuidShort(),
    val categoryBreakdownToolId: String = uuidShort(),
    val agentMemoryId: String = uuidShort(),
    val agentResponseId: String = uuidShort(),
    val listFilesId: String = uuidShort(),
    val listResponseId: String = uuidShort(),
    val deleteFilesId: String = uuidShort(),
    val deleteResponseId: String = uuidShort(),
    val helpNodeId: String = uuidShort(),
    val helpResponseId: String = uuidShort()
)
