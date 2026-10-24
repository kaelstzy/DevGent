package com.kaelzyy.devgent.agent

enum class Perm { ALLOW, ASK, DENY }

data class Agent(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val permissions: Map<String, Perm>,
    val model: String? = null,
    val builtIn: Boolean = false,
)

val TOOL_NAMES = listOf("read", "list", "search", "write", "edit")

val BUILT_IN_AGENTS: List<Agent> = listOf(
    Agent(
        id = "build",
        name = "Build",
        description = "Agent coding aktif: boleh baca, tulis, dan edit file (edit ditanya dulu).",
        systemPrompt = "Kamu adalah DevGent, agent coding yang bekerja di folder kerja user memakai tools. " +
            "Gunakan tools read, list, search, write, dan edit untuk membaca serta mengubah file; " +
            "semua path relatif terhadap folder kerja, tanpa path absolut atau segmen '..'. " +
            "Balas dengan bahasa Indonesia yang ringkas dan langsung ke inti.",
        permissions = mapOf(
            "read" to Perm.ALLOW,
            "list" to Perm.ALLOW,
            "search" to Perm.ALLOW,
            "write" to Perm.ASK,
            "edit" to Perm.ASK,
        ),
        builtIn = true,
    ),
    Agent(
        id = "plan",
        name = "Plan",
        description = "Analisis dan rencanakan saja; tidak boleh mengubah file.",
        systemPrompt = "Kamu adalah DevGent dalam mode perencanaan. Analisis folder kerja user memakai tools " +
            "read, list, dan search, lalu susun rencana langkah demi langkah. Jangan mengubah file apa pun. " +
            "Semua path relatif terhadap folder kerja, tanpa path absolut atau segmen '..'. " +
            "Balas dengan bahasa Indonesia yang ringkas dan langsung ke inti.",
        permissions = mapOf(
            "read" to Perm.ALLOW,
            "list" to Perm.ALLOW,
            "search" to Perm.ALLOW,
            "write" to Perm.DENY,
            "edit" to Perm.DENY,
        ),
        builtIn = true,
    ),
)
