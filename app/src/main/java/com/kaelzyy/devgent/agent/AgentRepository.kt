package com.kaelzyy.devgent.agent

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.agentsDataStore by preferencesDataStore(name = "agents")

class AgentRepository(private val context: Context) {

    val agents: Flow<List<Agent>> = context.agentsDataStore.data.map { prefs ->
        val customs = parseCustoms(prefs[KEY_CUSTOM_AGENTS])
        BUILT_IN_AGENTS + customs
    }

    val activeAgent: Flow<Agent> = context.agentsDataStore.data.map { prefs ->
        val customs = parseCustoms(prefs[KEY_CUSTOM_AGENTS])
        val all = BUILT_IN_AGENTS + customs
        val activeId = prefs[KEY_ACTIVE_ID] ?: BUILT_IN_AGENTS.first().id
        all.firstOrNull { it.id == activeId } ?: BUILT_IN_AGENTS.first()
    }

    suspend fun setActive(id: String) {
        val customs = parseCustoms(context.agentsDataStore.data.first()[KEY_CUSTOM_AGENTS])
        val exists = (BUILT_IN_AGENTS + customs).any { it.id == id }
        if (!exists) return
        context.agentsDataStore.edit { it[KEY_ACTIVE_ID] = id }
    }

    suspend fun saveCustom(agent: Agent) {
        require(!agent.builtIn) { "Agent built-in tidak bisa diubah." }
        val customs = parseCustoms(context.agentsDataStore.data.first()[KEY_CUSTOM_AGENTS]).toMutableList()
        val index = customs.indexOfFirst { it.id == agent.id }
        if (index >= 0) {
            customs[index] = agent
        } else {
            customs.add(agent)
        }
        writeCustoms(customs)
    }

    suspend fun deleteCustom(id: String) {
        val customs = parseCustoms(context.agentsDataStore.data.first()[KEY_CUSTOM_AGENTS])
            .filterNot { it.id == id }
        writeCustoms(customs)
        val activeId = context.agentsDataStore.data.first()[KEY_ACTIVE_ID]
        if (activeId == id) {
            context.agentsDataStore.edit { it[KEY_ACTIVE_ID] = BUILT_IN_AGENTS.first().id }
        }
    }

    suspend fun isNameTaken(name: String, excludeId: String? = null): Boolean {
        val customs = parseCustoms(context.agentsDataStore.data.first()[KEY_CUSTOM_AGENTS])
        return (BUILT_IN_AGENTS + customs).any {
            it.id != excludeId && it.name.equals(name.trim(), ignoreCase = true)
        }
    }

    private suspend fun writeCustoms(customs: List<Agent>) {
        val array = JSONArray()
        for (agent in customs) {
            val obj = JSONObject()
                .put("id", agent.id)
                .put("name", agent.name)
                .put("description", agent.description)
                .put("systemPrompt", agent.systemPrompt)
                .put("model", agent.model ?: JSONObject.NULL)
            val perms = JSONObject()
            for ((tool, perm) in agent.permissions) {
                perms.put(tool, perm.name)
            }
            obj.put("permissions", perms)
            array.put(obj)
        }
        context.agentsDataStore.edit { it[KEY_CUSTOM_AGENTS] = array.toString() }
    }

    private fun parseCustoms(raw: String?): List<Agent> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                runCatching {
                    val obj = array.getJSONObject(i)
                    val permsObj = obj.optJSONObject("permissions") ?: JSONObject()
                    val perms = mutableMapOf<String, Perm>()
                    for (tool in TOOL_NAMES) {
                        val name = permsObj.optString(tool, Perm.ASK.name)
                        perms[tool] = runCatching { Perm.valueOf(name) }.getOrDefault(Perm.ASK)
                    }
                    Agent(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        description = obj.optString("description"),
                        systemPrompt = obj.getString("systemPrompt"),
                        permissions = perms,
                        model = if (obj.isNull("model")) null else obj.optString("model"),
                        builtIn = false,
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        val KEY_CUSTOM_AGENTS = stringPreferencesKey("custom_agents_json")
        val KEY_ACTIVE_ID = stringPreferencesKey("active_agent_id")
    }
}
