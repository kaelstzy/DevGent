package com.kaelzyy.devgent.llm

import org.json.JSONObject

sealed interface ChatMessage {
    data class System(val content: String) : ChatMessage
    data class User(val content: String) : ChatMessage
    data class Assistant(
        val content: String?,
        val toolCalls: List<ToolCall> = emptyList(),
    ) : ChatMessage

    data class ToolResult(val toolCallId: String, val content: String) : ChatMessage
}

data class ToolCall(val id: String, val name: String, val arguments: String)

data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JSONObject,
)
