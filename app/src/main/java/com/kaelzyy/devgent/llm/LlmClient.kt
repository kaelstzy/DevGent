package com.kaelzyy.devgent.llm

import com.kaelzyy.devgent.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.TreeMap
import java.util.concurrent.TimeUnit

class LlmClient(private val settings: SettingsRepository) {

    class LlmException(message: String) : Exception(message)

    data class AssistantTurn(val content: String, val toolCalls: List<ToolCall>)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun testConnection() = withContext(Dispatchers.IO) {
        val key = requireKey()
        val app = settings.appSettings.first()
        val request = Request.Builder()
            .url(endpoint(app.baseUrl))
            .header("Authorization", "Bearer $key")
            .post(
                buildRequest(
                    model = app.model,
                    messages = listOf(ChatMessage.User("ping")),
                    tools = emptyList(),
                    stream = false,
                ).toRequestBody(),
            )
            .build()
        val call = http.newCall(request)
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw LlmException(errorMessage(resp))
            }
        } finally {
            call.cancel()
        }
    }

    suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        onText: (String) -> Unit,
    ): AssistantTurn = withContext(Dispatchers.IO) {
        val key = requireKey()
        val app = settings.appSettings.first()
        val request = Request.Builder()
            .url(endpoint(app.baseUrl))
            .header("Authorization", "Bearer $key")
            .header("Accept", "text/event-stream")
            .post(
                buildRequest(
                    model = app.model,
                    messages = messages,
                    tools = tools,
                    stream = true,
                ).toRequestBody(),
            )
            .build()
        val call = http.newCall(request)
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw LlmException(errorMessage(resp))
                val body = resp.body ?: throw LlmException("Respons kosong.")
                val content = StringBuilder()
                val calls = TreeMap<Int, PendingToolCall>()
                body.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        ensureActive()
                        val data = if (line.startsWith("data:")) line.substring(5).trim() else null
                        if (!data.isNullOrEmpty() && data != "[DONE]") {
                            handleChunk(data, content, calls, onText)
                        }
                        line = reader.readLine()
                    }
                }
                AssistantTurn(
                    content = content.toString(),
                    toolCalls = calls.map { (index, pending) -> pending.toCall(index) },
                )
            }
        } finally {
            call.cancel()
        }
    }

    private suspend fun requireKey(): String =
        settings.getApiKey() ?: throw LlmException("API key belum diisi.")

    private fun endpoint(baseUrl: String): HttpUrl {
        val clean = baseUrl.trim().trimEnd('/')
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            throw LlmException("Base URL harus diawali http:// atau https://.")
        }
        val url = "$clean/chat/completions".toHttpUrlOrNull()
            ?: throw LlmException("Base URL tidak valid.")
        return url
    }

    private fun buildRequest(
        model: String,
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        stream: Boolean,
    ): JSONObject = JSONObject().apply {
        put("model", model)
        put("stream", stream)
        put("messages", JSONArray(messages.map { messageJson(it) }))
        if (tools.isNotEmpty()) {
            put(
                "tools",
                JSONArray(tools.map { spec ->
                    JSONObject().apply {
                        put("type", "function")
                        put(
                            "function",
                            JSONObject().apply {
                                put("name", spec.name)
                                put("description", spec.description)
                                put("parameters", spec.parameters)
                            },
                        )
                    }
                }),
            )
        }
    }

    private fun messageJson(message: ChatMessage): JSONObject = when (message) {
        is ChatMessage.System -> JSONObject().put("role", "system").put("content", message.content)
        is ChatMessage.User -> JSONObject().put("role", "user").put("content", message.content)
        is ChatMessage.Assistant -> JSONObject().put("role", "assistant").apply {
            put("content", message.content ?: JSONObject.NULL)
            if (message.toolCalls.isNotEmpty()) {
                put(
                    "tool_calls",
                    JSONArray(message.toolCalls.map { call ->
                        JSONObject().apply {
                            put("id", call.id)
                            put("type", "function")
                            put(
                                "function",
                                JSONObject().apply {
                                    put("name", call.name)
                                    put("arguments", call.arguments)
                                },
                            )
                        }
                    }),
                )
            }
        }
        is ChatMessage.ToolResult -> JSONObject()
            .put("role", "tool")
            .put("tool_call_id", message.toolCallId)
            .put("content", message.content)
    }

    private fun handleChunk(
        data: String,
        content: StringBuilder,
        calls: MutableMap<Int, PendingToolCall>,
        onText: (String) -> Unit,
    ) {
        val json = try {
            JSONObject(data)
        } catch (_: JSONException) {
            return
        }
        val choices = json.optJSONArray("choices") ?: return
        if (choices.length() == 0) return
        val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return
        val piece = delta.optString("content", "")
        if (piece.isNotEmpty()) {
            content.append(piece)
            onText(piece)
        }
        val toolCalls = delta.optJSONArray("tool_calls") ?: return
        for (i in 0 until toolCalls.length()) {
            val item = toolCalls.optJSONObject(i) ?: continue
            val index = item.optInt("index", i)
            val pending = calls.getOrPut(index) { PendingToolCall() }
            val id = item.optString("id", "")
            if (id.isNotEmpty()) pending.id = id
            val function = item.optJSONObject("function") ?: continue
            val name = function.optString("name", "")
            if (name.isNotEmpty()) pending.name = name
            val arguments = function.optString("arguments", "")
            if (arguments.isNotEmpty()) pending.arguments.append(arguments)
        }
    }

    private fun errorMessage(resp: okhttp3.Response): String {
        val raw = try {
            resp.body?.string().orEmpty().take(4000)
        } catch (_: IOException) {
            ""
        }
        val fromBody = runCatching {
            JSONObject(raw).getJSONObject("error").getString("message")
        }.getOrNull()
        if (fromBody != null) return fromBody.take(300)
        return when (resp.code) {
            401, 403 -> "HTTP ${resp.code}: API key ditolak."
            404 -> "HTTP 404: URL atau model tidak ditemukan."
            429 -> "HTTP 429: rate limit tercapai."
            else -> "HTTP ${resp.code}"
        }
    }

    private class PendingToolCall {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()

        fun toCall(index: Int) = ToolCall(
            id = id.ifEmpty { "call_$index" },
            name = name,
            arguments = arguments.toString(),
        )
    }

    private fun JSONObject.toRequestBody(): RequestBody =
        toString().toRequestBody(JSON_MEDIA_TYPE)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
