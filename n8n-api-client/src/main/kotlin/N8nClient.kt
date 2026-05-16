import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class N8nClient(
    private val baseUrl: String,
    private val apiKey: String
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
        install(Logging) {
            level = LogLevel.HEADERS
        }
        defaultRequest {
            header("X-N8N-API-KEY", apiKey)
            contentType(ContentType.Application.Json)
        }
        expectSuccess = false
    }

    private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"

    private val apiBase: String
        get() = "${baseUrl.ensureTrailingSlash()}api/v1/"

    // --- Workflows ---

    suspend fun listWorkflows(): List<Workflow> {
        val response: HttpResponse = client.get("${apiBase}workflows")
        return handleResponse<WorkflowListResponse>(response).data
    }

    suspend fun getWorkflow(id: String): Workflow {
        val response: HttpResponse = client.get("${apiBase}workflows/$id")
        return handleResponse(response)
    }

    suspend fun createWorkflow(name: String): Workflow {
        val response: HttpResponse = client.post("${apiBase}workflows") {
            setBody(WorkflowCreateRequest(name = name))
        }
        return handleResponse(response)
    }

    suspend fun createFullWorkflow(request: WorkflowCreateRequest): Workflow {
        val response: HttpResponse = client.post("${apiBase}workflows") {
            setBody(request)
        }
        return handleResponse(response)
    }

    suspend fun updateWorkflow(id: String, workflow: Workflow): Workflow {
        val response: HttpResponse = client.patch("${apiBase}workflows/$id") {
            setBody(workflow)
        }
        return handleResponse(response)
    }

    suspend fun deleteWorkflow(id: String): Boolean {
        val response: HttpResponse = client.delete("${apiBase}workflows/$id")
        return response.status == HttpStatusCode.OK
    }

    suspend fun activateWorkflow(id: String): Workflow {
        val response: HttpResponse = client.post("${apiBase}workflows/$id/activate")
        return handleResponse(response)
    }

    suspend fun deactivateWorkflow(id: String): Workflow {
        val response: HttpResponse = client.post("${apiBase}workflows/$id/deactivate")
        return handleResponse(response)
    }

    suspend fun executeWorkflow(id: String): Execution {
        val response: HttpResponse = client.post("${apiBase}workflows/$id/execute")
        return handleResponse(response)
    }

    // --- Executions ---

    suspend fun listExecutions(): List<Execution> {
        val response: HttpResponse = client.get("${apiBase}executions")
        return handleResponse<ExecutionListResponse>(response).data
    }

    suspend fun getExecution(id: String): Execution {
        val response: HttpResponse = client.get("${apiBase}executions/$id")
        return handleResponse(response)
    }

    // --- Generic Response Handler ---

    private suspend inline fun <reified T> handleResponse(response: HttpResponse): T {
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val errorBody = response.bodyAsText()
            throw N8nApiException(
                "HTTP ${response.status.value}: $errorBody",
                response.status.value
            )
        }
    }

    fun close() {
        client.close()
    }
}

class N8nApiException(message: String, val statusCode: Int) : Exception(message)
