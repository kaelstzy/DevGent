package com.kaelzyy.devgent.llm

enum class ApiFormat { OPENAI, ANTHROPIC, GEMINI }

data class ProviderPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val models: List<String>,
    val format: ApiFormat,
    val supportsReasoning: Boolean = false,
    val extraApiKeyHeader: Boolean = false,
)

object Providers {

    val REASONING_LEVELS = listOf("off", "minimal", "low", "medium", "high")

    val PRESETS = listOf(
        ProviderPreset(
            id = "openai",
            label = "OpenAI (GPT)",
            baseUrl = "https://api.openai.com/v1",
            models = listOf(
                "gpt-5",
                "gpt-5-mini",
                "gpt-4.1",
                "gpt-4.1-mini",
                "gpt-4o",
                "gpt-4o-mini",
            ),
            format = ApiFormat.OPENAI,
            supportsReasoning = true,
        ),
        ProviderPreset(
            id = "anthropic",
            label = "Anthropic (Claude)",
            baseUrl = "https://api.anthropic.com/v1",
            models = listOf(
                "claude-sonnet-4-5",
                "claude-opus-4-1",
                "claude-haiku-4-5",
            ),
            format = ApiFormat.ANTHROPIC,
        ),
        ProviderPreset(
            id = "gemini",
            label = "Google Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            models = listOf(
                "gemini-2.5-pro",
                "gemini-2.5-flash",
                "gemini-2.0-flash",
            ),
            format = ApiFormat.GEMINI,
        ),
        ProviderPreset(
            id = "deepseek",
            label = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            models = listOf(
                "deepseek-chat",
                "deepseek-reasoner",
            ),
            format = ApiFormat.OPENAI,
        ),
        ProviderPreset(
            id = "groq",
            label = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            models = listOf(
                "llama-3.3-70b-versatile",
                "openai/gpt-oss-120b",
            ),
            format = ApiFormat.OPENAI,
        ),
        ProviderPreset(
            id = "openrouter",
            label = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            models = listOf(
                "openai/gpt-4o",
                "anthropic/claude-sonnet-4",
                "google/gemini-2.5-flash",
            ),
            format = ApiFormat.OPENAI,
        ),
        ProviderPreset(
            id = "mimo",
            label = "MiMo (Xiaomi)",
            baseUrl = "https://api.xiaomimimo.com/v1",
            models = listOf(
                "mimo-v2.6-pro",
                "mimo-v2.6-flash",
            ),
            format = ApiFormat.OPENAI,
            extraApiKeyHeader = true,
        ),
        ProviderPreset(
            id = "custom",
            label = "Custom (OpenAI-compatible)",
            baseUrl = "",
            models = emptyList(),
            format = ApiFormat.OPENAI,
        ),
    )

    fun byId(id: String): ProviderPreset = PRESETS.firstOrNull { it.id == id } ?: PRESETS.first()
}
