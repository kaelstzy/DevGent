package com.kaelzyy.devgent.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class QuestionItem(
    val question: String,
    val options: List<String>,
    val multiSelect: Boolean,
    val allowCustom: Boolean,
)

data class QuestionPayload(val items: List<QuestionItem>)

data class PendingQuestion(val toolCallId: String, val payload: QuestionPayload)

class QuestionGate {

    private val _pending = MutableStateFlow<QuestionPayload?>(null)
    val pending: StateFlow<QuestionPayload?> = _pending

    private var waiter: CompletableDeferred<String?>? = null

    fun hasWaiter(): Boolean = waiter != null

    suspend fun ask(payload: QuestionPayload): String? {
        val deferred = CompletableDeferred<String?>()
        waiter = deferred
        _pending.value = payload
        try {
            return deferred.await()
        } finally {
            waiter = null
            _pending.value = null
        }
    }

    fun restorePending(payload: QuestionPayload) {
        _pending.value = payload
    }

    fun clearPending() {
        _pending.value = null
    }

    fun submit(answer: String) {
        waiter?.complete(answer)
    }

    fun skip() {
        waiter?.complete(null)
    }
}

object QuestionParser {

    const val MAX_QUESTIONS = 3
    const val MIN_OPTIONS = 2
    const val MAX_OPTIONS = 4

    fun parse(arguments: String): List<QuestionItem> {
        val args = try {
            if (arguments.isBlank()) JSONObject() else JSONObject(arguments)
        } catch (e: Exception) {
            throw IllegalArgumentException("Argumen question bukan JSON valid.")
        }
        val array = args.optJSONArray("questions")
            ?: throw IllegalArgumentException("Parameter \"questions\" wajib.")
        if (array.length() == 0) {
            throw IllegalArgumentException("Daftar questions kosong.")
        }
        if (array.length() > MAX_QUESTIONS) {
            throw IllegalArgumentException("Maksimal $MAX_QUESTIONS pertanyaan per panggilan.")
        }
        return (0 until array.length()).map { i ->
            val obj = array.optJSONObject(i)
                ?: throw IllegalArgumentException("Item ke-${i + 1} bukan objek.")
            val question = obj.optString("question").trim()
            if (question.isEmpty()) {
                throw IllegalArgumentException("Pertanyaan ke-${i + 1} kosong.")
            }
            val optionsArray = obj.optJSONArray("options")
                ?: throw IllegalArgumentException("Pertanyaan \"$question\" tanpa options.")
            val options = (0 until optionsArray.length()).map { optionsArray.optString(it).trim() }
            if (options.any { it.isEmpty() }) {
                throw IllegalArgumentException("Ada option kosong di \"$question\".")
            }
            if (options.size < MIN_OPTIONS || options.size > MAX_OPTIONS) {
                throw IllegalArgumentException(
                    "Option \"$question\" harus $MIN_OPTIONS–$MAX_OPTIONS buah.",
                )
            }
            QuestionItem(
                question = question,
                options = options,
                multiSelect = obj.optBoolean("multiSelect", false),
                allowCustom = obj.optBoolean("allowCustom", false),
            )
        }
    }
}

fun PendingQuestion.toJson(): JSONObject = JSONObject()
    .put("toolCallId", toolCallId)
    .put("questions", JSONArray(payload.items.map { item ->
        JSONObject()
            .put("question", item.question)
            .put("options", JSONArray(item.options))
            .put("multiSelect", item.multiSelect)
            .put("allowCustom", item.allowCustom)
    }))

fun pendingQuestionFromJson(raw: String?): PendingQuestion? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val obj = JSONObject(raw)
        val array = obj.optJSONArray("questions") ?: return null
        if (array.length() == 0) return null
        val items = (0 until array.length()).mapNotNull { i ->
            runCatching {
                val item = array.getJSONObject(i)
                val optionsArray = item.optJSONArray("options") ?: return@runCatching null
                val options = (0 until optionsArray.length()).map { optionsArray.optString(it) }
                if (options.size < QuestionParser.MIN_OPTIONS) return@runCatching null
                QuestionItem(
                    question = item.getString("question"),
                    options = options,
                    multiSelect = item.optBoolean("multiSelect", false),
                    allowCustom = item.optBoolean("allowCustom", false),
                )
            }.getOrNull()
        }
        if (items.isEmpty()) return null
        PendingQuestion(obj.getString("toolCallId"), QuestionPayload(items))
    }.getOrNull()
}
