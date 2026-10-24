package com.kaelzyy.devgent.llm

import com.kaelzyy.devgent.settings.AppSettings
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
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun testConnection() = withContext(Dispatchers.IO) {
        val key = requireKey()
        val app = settings.appSettings.first()
        val preset = Providers.byId(app.provider)
        val request = buildRequest(
            preset = preset,
            app = app,
            key = key,
            messages = listOf(ChatMessage.User("ping")),
            tools = emptyList(),
            stream = false,
        )
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
        val preset = Providers.byId(app.provider)
        val request = buildRequest(preset, app, key, messages, tools, stream = true)
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
                            when (preset.format) {
                                ApiFormat.OPENAI -> handleOpenAiChunk(data, content, calls, onText)
                                ApiFormat.ANTHROPIC -> handleAnthropicEvent(data, content, calls, onText)
                                ApiFormat.GEMINI -> handleGeminiChunk(data, content, calls, onText)
                            }
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

    private fun buildRequest(
        preset: ProviderPreset,
        app: AppSettings,
        key: String,
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        stream: Boolean,
    ): Request {
        val builder = Request.Builder()
        if (stream) builder.header("Accept", "text/event-stream")
        when (preset.format) {
            ApiFormat.OPENAI -> {
                builder
                    .url(openAiEndpoint(app.baseUrl))
                    .header("Authorization", "Bearer $key")
                if (preset.extraApiKeyHeader) builder.header("api-key", key)
                builder.post(
                    openAiBody(preset, app, messages, tools, stream).toRequestBody(),
                )
            }
            ApiFormat.ANTHROPIC -> {
                builder
                    .url(anthropicEndpoint(app.baseUrl))
                    .header("x-api-key", key)
                    .header("anthropic-version", "2023-06-01")
                builder.post(
                    anthropicBody(app.model, messages, tools, stream).toRequestBody(),
                )
            }
            ApiFormat.GEMINI -> {
                builder
                    .url(geminiEndpoint(app.baseUrl, app.model, stream))
                    .header("x-goog-api-key", key)
                builder.post(
                    geminiBody(messages, tools).toRequestBody(),
                )
            }
        }
        return builder.build()
    }

    private fun openAiEndpoint(baseUrl: String): HttpUrl {
        val clean = baseUrl.trim().trimEnd('/')
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            throw LlmException("Base URL harus diawali http:// atau https://.")
        }
        return "$clean/chat/completions".toHttpUrlOrNull()
            ?: throw LlmException("Base URL tidak valid.")
    }

    private fun anthropicEndpoint(baseUrl: String): HttpUrl {
        val clean = baseUrl.trim().trimEnd('/')
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            throw LlmException("Base URL harus diawali http:// atau https://.")
        }
        return "$clean/messages".toHttpUrlOrNull()
            ?: throw LlmException("Base URL tidak valid.")
    }

    private fun geminiEndpoint(baseUrl: String, model: String, stream: Boolean): HttpUrl {
        val clean = baseUrl.trim().trimEnd('/')
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            throw LlmException("Base URL harus diawali http:// atau https://.")
        }
        val action = if (stream) "streamGenerateContent?alt=sse" else "generateContent"
        return "$clean/models/$model:$action".toHttpUrlOrNull()
            ?: throw LlmException("Base URL tidak valid.")
    }

    private fun openAiBody(
        preset: ProviderPreset,
        app: AppSettings,
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        stream: Boolean,
    ): JSONObject = JSONObject().apply {
        put("model", app.model)
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
        if (preset.supportsReasoning && app.reasoning != "off") {
            put("reasoning_effort", app.reasoning)
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

    private fun anthropicBody(
        model: String,
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        stream: Boolean,
    ): JSONObject {
        val system = StringBuilder()
        val out = JSONArray()
        for (message in messages) {
            when (message) {
                is ChatMessage.System -> {
                    if (system.isNotEmpty()) system.append("\n\n")
                    system.append(message.content)
                }
                is ChatMessage.User -> out.put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", JSONArray().put(textPart(message.content))),
                )
                is ChatMessage.Assistant -> {
                    val parts = JSONArray()
                    message.content
                        ?.takeIf { it.isNotBlank() }
                        ?.let { parts.put(textPart(it)) }
                    for (call in message.toolCalls) {
                        parts.put(
                            JSONObject()
                                .put("type", "tool_use")
                                .put("id", call.id)
                                .put("name", call.name)
                                .put("input", jsonObjectOrEmpty(call.arguments)),
                        )
                    }
                    if (parts.length() == 0) parts.put(textPart("(tidak ada isi)"))
                    out.put(JSONObject().put("role", "assistant").put("content", parts))
                }
                is ChatMessage.ToolResult -> out.put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "content",
                            JSONArray().put(
                                JSONObject()
                                    .put("type", "tool_result")
                                    .put("tool_use_id", message.toolCallId)
                                    .put("content", message.content),
                            ),
                        ),
                )
            }
        }
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 8192)
            .put("messages", out)
            .put("stream", stream)
        if (system.isNotEmpty()) body.put("system", system.toString())
        if (tools.isNotEmpty()) {
            body.put(
                "tools",
                JSONArray(tools.map { spec ->
                    JSONObject()
                        .put("name", spec.name)
                        .put("description", spec.description)
                        .put("input_schema", spec.parameters)
                }),
            )
        }
        return body
    }

    private fun geminiBody(messages: List<ChatMessage>, tools: List<ToolSpec>): JSONObject {
        val system = StringBuilder()
        val contents = JSONArray()
        for (message in messages) {
            when (message) {
                is ChatMessage.System -> {
                    if (system.isNotEmpty()) system.append("\n\n")
                    system.append(message.content)
                }
                is ChatMessage.User -> contents.put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", message.content))),
                )
                is ChatMessage.Assistant -> {
                    val parts = JSONArray()
                    message.content
                        ?.takeIf { it.isNotBlank() }
                        ?.let { parts.put(JSONObject().put("text", it)) }
                    for (call in message.toolCalls) {
                        parts.put(
                            JSONObject().put(
                                "functionCall",
                                JSONObject()
                                    .put("name", call.name)
                                    .put("args", jsonObjectOrEmpty(call.arguments)),
                            ),
                        )
                    }
                    if (parts.length() == 0) parts.put(JSONObject().put("text", "(tidak ada isi)"))
                    contents.put(
                        JSONObject()
                            .put("role", "model")
                            .put("parts", parts),
                    )
                }
                is ChatMessage.ToolResult -> {
                    val name = findToolName(messages, message.toolCallId)
                    contents.put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().put(
                                        "functionResponse",
                                        JSONObject()
                                            .put("name", name)
                                            .put(
                                                "response",
                                                JSONObject().put("output", message.content),
                                            ),
                                    ),
                                ),
                            ),
                    )
                }
            }
        }
        val body = JSONObject().put("contents", contents)
        if (system.isNotEmpty()) {
            body.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system.toString()))),
            )
        }
        if (tools.isNotEmpty()) {
            body.put(
                "tools",
                JSONArray().put(
                    JSONObject().put(
                        "functionDeclarations",
                        JSONArray(tools.map { spec ->
                            JSONObject()
                                .put("name", spec.name)
                                .put("description", spec.description)
                                .put("parameters", spec.parameters)
                        }),
                    ),
                ),
            )
        }
        return body
    }

    private fun findToolName(messages: List<ChatMessage>, toolCallId: String): String {
        for (message in messages) {
            if (message is ChatMessage.Assistant) {
                val match = message.toolCalls.firstOrNull { it.id == toolCallId }
                if (match != null) return match.name
            }
        }
        return "unknown"
    }

    private fun textPart(text: String): JSONObject =
        JSONObject().put("type", "text").put("text", text)

    private fun jsonObjectOrEmpty(raw: String): JSONObject =
        if (raw.isBlank()) JSONObject()
        else try {
            JSONObject(raw)
        } catch (_: JSONException) {
            JSONObject()
        }

    private fun handleOpenAiChunk(
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

    private fun handleAnthropicEvent(
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
        when (json.optString("type")) {
            "content_block_start" -> {
                val block = json.optJSONObject("content_block") ?: return
                if (block.optString("type") != "tool_use") return
                val index = json.optInt("index", 0)
                val pending = calls.getOrPut(index) { PendingToolCall() }
                val id = block.optString("id", "")
                if (id.isNotEmpty()) pending.id = id
                val name = block.optString("name", "")
                if (name.isNotEmpty()) pending.name = name
            }
            "content_block_delta" -> {
                val delta = json.optJSONObject("delta") ?: return
                when (delta.optString("type")) {
                    "text_delta" -> {
                        val piece = delta.optString("text", "")
                        if (piece.isNotEmpty()) {
                            content.append(piece)
                            onText(piece)
                        }
                    }
                    "input_json_delta" -> {
                        val piece = delta.optString("partial_json", "")
                        if (piece.isNotEmpty()) {
                            val index = json.optInt("index", 0)
                            calls.getOrPut(index) { PendingToolCall() }.arguments.append(piece)
                        }
                    }
                }
            }
            "error" -> {
                val message = json.optJSONObject("error")?.optString("message").orEmpty()
                throw LlmException(message.ifEmpty { "Kesalahan dari server." }.take(300))
            }
        }
    }

    private fun handleGeminiChunk(
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
        val error = json.optJSONObject("error")
        if (error != null) {
            throw LlmException(
                error.optString("message", "Kesalahan dari server.").take(300),
            )
        }
        val candidates = json.optJSONArray("candidates") ?: return
        for (i in 0 until candidates.length()) {
            val parts = candidates.optJSONObject(i)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: continue
            for (j in 0 until parts.length()) {
                val part = parts.optJSONObject(j) ?: continue
                val piece = part.optString("text", "")
                if (piece.isNotEmpty()) {
                    content.append(piece)
                    onText(piece)
                }
                val call = part.optJSONObject("functionCall") ?: continue
                val pending = PendingToolCall()
                pending.name = call.optString("name", "")
                pending.arguments.append(call.optJSONObject("args")?.toString() ?: "{}")
                calls[calls.size] = pending
            }
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
