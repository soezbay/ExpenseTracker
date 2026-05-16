import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Workflow(
    val id: String? = null,
    val name: String,
    val active: Boolean = false,
    val nodes: List<JsonElement> = emptyList(),
    val connections: JsonElement? = null,
    val settings: JsonElement? = null,
    val tags: List<String>? = null
)

@Serializable
data class WorkflowListResponse(
    val data: List<Workflow>,
    val nextCursor: String? = null
)

@Serializable
data class WorkflowCreateRequest(
    val name: String,
    val nodes: List<JsonElement> = emptyList(),
    val connections: JsonElement? = null,
    val settings: JsonElement? = null
)

@Serializable
data class Execution(
    val id: String,
    val workflowId: String,
    val finished: Boolean,
    val mode: String,
    val startedAt: String,
    val stoppedAt: String? = null,
    val status: String
)

@Serializable
data class ExecutionListResponse(
    val data: List<Execution>,
    val nextCursor: String? = null
)

@Serializable
data class ApiError(
    val message: String,
    val httpStatusCode: Int? = null,
    val code: String? = null
)
