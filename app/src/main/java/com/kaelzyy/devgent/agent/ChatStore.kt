package com.kaelzyy.devgent.agent

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kaelzyy.devgent.llm.ChatMessage
import com.kaelzyy.devgent.llm.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.chatDataStore by preferencesDataStore(name = "chat")

data class SessionMeta(
    val id: String,
    val title: String,
    val updatedAt: Long,
)

class ChatStore(private val context: Context) {

    val sessions: Flow<List<SessionMeta>> = context.chatDataStore.data.map { prefs ->
        parseMetas(prefs[KEY_SESSIONS]).sortedByDescending { it.updatedAt }
    }

    suspend fun activeSessionId(): String? =
        context.chatDataStore.data.first()[KEY_ACTIVE_SESSION]

    suspend fun createSession(): SessionMeta {
        val meta = SessionMeta(
            id = "s${System.currentTimeMillis()}",
            title = "",
            updatedAt = System.currentTimeMillis(),
        )
        val prefs = context.chatDataStore.data.first()
        val metas = parseMetas(prefs[KEY_SESSIONS]) + meta
        context.chatDataStore.edit {
            it[KEY_SESSIONS] = metasToJson(metas)
            it[KEY_ACTIVE_SESSION] = meta.id
        }
        return meta
    }

    suspend fun activate(id: String) {
        context.chatDataStore.edit { it[KEY_ACTIVE_SESSION] = id }
    }

    suspend fun updateTitle(id: String, title: String) {
        if (title.isBlank()) return
        val prefs = context.chatDataStore.data.first()
        val metas = parseMetas(prefs[KEY_SESSIONS]).map {
            if (it.id == id && it.title.isEmpty()) it.copy(title = title) else it
        }
        context.chatDataStore.edit { it[KEY_SESSIONS] = metasToJson(metas) }
    }

    suspend fun touch(id: String) {
        val prefs = context.chatDataStore.data.first()
        val metas = parseMetas(prefs[KEY_SESSIONS]).map {
            if (it.id == id) it.copy(updatedAt = System.currentTimeMillis()) else it
        }
        context.chatDataStore.edit { it[KEY_SESSIONS] = metasToJson(metas) }
    }

    suspend fun saveHistory(sessionId: String, messages: List<ChatMessage>) {
        val array = JSONArray()
        for (message in messages) {
            array.put(messageToJson(message))
        }
        context.chatDataStore.edit { it[historyKey(sessionId)] = array.toString() }
    }

    suspend fun loadHistory(sessionId: String): List<ChatMessage> {
        val raw = context.chatDataStore.data.first()[historyKey(sessionId)] ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                runCatching { messageFromJson(array.getJSONObject(i)) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    suspend fun savePending(sessionId: String, pending: PendingQuestion?) {
        context.chatDataStore.edit {
            if (pending == null) {
                it.remove(pendingKey(sessionId))
            } else {
                it[pendingKey(sessionId)] = pending.toJson().toString()
            }
        }
    }

    suspend fun loadPending(sessionId: String): PendingQuestion? {
        val raw = context.chatDataStore.data.first()[pendingKey(sessionId)]
        return pendingQuestionFromJson(raw)
    }

    private fun historyKey(id: String) = stringPreferencesKey("history_$id")
    private fun pendingKey(id: String) = stringPreferencesKey("pending_$id")

    private fun parseMetas(raw: String?): List<SessionMeta> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                runCatching {
                    val obj = array.getJSONObject(i)
                    SessionMeta(
                        id = obj.getString("id"),
                        title = obj.optString("title"),
                        updatedAt = obj.optLong("updatedAt", 0L),
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun metasToJson(metas: List<SessionMeta>): String {
        val array = JSONArray()
        for (meta in metas) {
            array.put(
                JSONObject()
                    .put("id", meta.id)
                    .put("title", meta.title)
                    .put("updatedAt", meta.updatedAt),
            )
        }
        return array.toString()
    }

    private fun messageToJson(message: ChatMessage): JSONObject = when (message) {
        is ChatMessage.System -> JSONObject().put("role", "system").put("content", message.content)
        is ChatMessage.User -> JSONObject().put("role", "user").put("content", message.content)
        is ChatMessage.Assistant -> JSONObject().put("role", "assistant").apply {
            put("content", message.content ?: JSONObject.NULL)
            if (message.toolCalls.isNotEmpty()) {
                put(
                    "tool_calls",
                    JSONArray(message.toolCalls.map { call ->
                        JSONObject()
                            .put("id", call.id)
                            .put("name", call.name)
                            .put("arguments", call.arguments)
                    }),
                )
            }
        }
        is ChatMessage.ToolResult -> JSONObject()
            .put("role", "tool")
            .put("tool_call_id", message.toolCallId)
            .put("content", message.content)
    }

    private fun messageFromJson(obj: JSONObject): ChatMessage? = when (obj.optString("role")) {
        "system" -> ChatMessage.System(obj.optString("content"))
        "user" -> ChatMessage.User(obj.optString("content"))
        "assistant" -> {
            val contentRaw = obj.opt("content")
            val content = when (contentRaw) {
                null, JSONObject.NULL -> null
                else -> contentRaw.toString()
            }
            val callsArray = obj.optJSONArray("tool_calls")
            val calls = if (callsArray == null) {
                emptyList()
            } else {
                (0 until callsArray.length()).mapNotNull { i ->
                    runCatching {
                        val call = callsArray.getJSONObject(i)
                        ToolCall(
                            id = call.getString("id"),
                            name = call.getString("name"),
                            arguments = call.optString("arguments"),
                        )
                    }.getOrNull()
                }
            }
            ChatMessage.Assistant(content, calls)
        }
        "tool" -> ChatMessage.ToolResult(
            toolCallId = obj.optString("tool_call_id"),
            content = obj.optString("content"),
        )
        else -> null
    }

    private companion object {
        val KEY_SESSIONS = stringPreferencesKey("sessions_json")
        val KEY_ACTIVE_SESSION = stringPreferencesKey("active_session_id")
    }
}
