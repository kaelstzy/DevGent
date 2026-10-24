package com.kaelzyy.devgent.agent

import android.content.Context
import com.kaelzyy.devgent.llm.ChatMessage
import com.kaelzyy.devgent.llm.LlmClient
import com.kaelzyy.devgent.llm.ToolCall
import com.kaelzyy.devgent.settings.SettingsRepository
import com.kaelzyy.devgent.tools.DiffPreview
import com.kaelzyy.devgent.tools.FileTools
import com.kaelzyy.devgent.tools.PreviewResult
import com.kaelzyy.devgent.tools.ToolExecutor
import com.kaelzyy.devgent.workspace.WorkspaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ChatEntry {
    data class User(val text: String) : ChatEntry
    data class Agent(val text: String) : ChatEntry
    data class Error(val text: String) : ChatEntry
    data class Permission(
        val request: PermissionRequest,
        val resolved: PermissionResolution? = null,
    ) : ChatEntry

    data class ToolSteps(
        val steps: List<Step>,
        val expanded: Boolean = false,
        val done: Boolean = false,
    ) : ChatEntry {
        data class Step(val tool: String, val result: String)

        val readCount: Int get() = steps.count { it.tool == "read" }
        val hasList: Boolean get() = steps.any { it.tool == "list" }
        val hasSearch: Boolean get() = steps.any { it.tool == "search" }

        val label: String
            get() {
                val parts = mutableListOf<String>()
                if (readCount > 0) parts.add("Membaca $readCount file")
                if (hasList) parts.add("Membaca isi folder")
                if (hasSearch) parts.add("Mencari teks")
                return parts.joinToString(" · ")
            }
    }
}

enum class PermissionResolution { GRANTED_ONCE, GRANTED_SESSION, DENIED, INTERRUPTED }

data class ChatState(
    val entries: List<ChatEntry> = emptyList(),
    val liveText: String = "",
    val running: Boolean = false,
    val status: String = "",
)

data class FileChange(val path: String, val before: String?, val after: String)

class AgentSession private constructor(context: Context) {

    private val settings = SettingsRepository(context)
    private val workspace = WorkspaceRepository(context)
    private val client = LlmClient(settings)
    private val fileTools = FileTools(context.contentResolver)
    private val executor = ToolExecutor(context, workspace, fileTools)
    private val gate = PermissionGate()
    private val agents = AgentRepository(context)
    private val questionGate = QuestionGate()
    private val store = ChatStore(context)
    private var pendingToolCallId: String? = null
    private var sessionId: String = ""

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val history = mutableListOf<ChatMessage>()
    private var job: Job? = null

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state

    private val _changedFiles = MutableStateFlow<List<FileChange>>(emptyList())
    val changedFiles: StateFlow<List<FileChange>> = _changedFiles

    private val _activeSessionId = MutableStateFlow("")
    val activeSessionId: StateFlow<String> = _activeSessionId

    val activeAgent: Flow<Agent> = agents.activeAgent
    val pendingQuestion: StateFlow<QuestionPayload?> = questionGate.pending
    val sessions: Flow<List<SessionMeta>> = store.sessions

    init {
        scope.launch { boot() }
    }

    private suspend fun boot() {
        sessionId = store.activeSessionId() ?: store.createSession().id
        _activeSessionId.value = sessionId
        restoreSession(sessionId)
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || _state.value.running) return
        if (sessionId.isNotEmpty()) {
            scope.launch {
                store.updateTitle(sessionId, message.take(40))
                store.touch(sessionId)
            }
        }
        rememberMessage(ChatMessage.User(message))
        _state.update {
            it.copy(
                entries = it.entries + ChatEntry.User(message),
                liveText = "",
                running = true,
                status = "Mengirim pesan...",
            )
        }
        job = scope.launch { runLoop() }
    }

    fun stop() {
        job?.cancel()
    }

    fun newChat() {
        if (_state.value.running) job?.cancel()
        scope.launch {
            val meta = store.createSession()
            resetRuntime(meta.id)
            _state.value = ChatState()
        }
    }

    fun openSession(id: String) {
        if (id == sessionId || id.isEmpty()) return
        if (_state.value.running) job?.cancel()
        scope.launch {
            store.activate(id)
            resetRuntime(id)
            _state.value = ChatState()
            restoreSession(id)
        }
    }

    private fun resetRuntime(id: String) {
        sessionId = id
        _activeSessionId.value = id
        history.clear()
        pendingToolCallId = null
        questionGate.clearPending()
        _changedFiles.value = emptyList()
        storeSavePending(null)
    }

    fun respondPermission(decision: PermissionDecision) {
        gate.decide(decision)
    }

    fun respondQuestion(answer: String?) {
        if (questionGate.hasWaiter()) {
            if (answer == null) questionGate.skip() else questionGate.submit(answer)
            return
        }
        val toolCallId = pendingToolCallId ?: return
        if (questionGate.pending.value == null) return
        scope.launch {
            questionGate.clearPending()
            storeSavePending(null)
            pendingToolCallId = null
            rememberMessage(
                ChatMessage.ToolResult(toolCallId, answer ?: "User melewati pertanyaan."),
            )
            _state.update { it.copy(running = true, status = "Melanjutkan...") }
            job = scope.launch { runLoop() }
        }
    }

    fun switchAgent(id: String) {
        scope.launch { agents.setActive(id) }
    }

    fun toggleToolSteps(index: Int) {
        _state.update { s ->
            val entry = s.entries.getOrNull(index)
            if (entry !is ChatEntry.ToolSteps) {
                s
            } else {
                val updated = s.entries.toMutableList()
                updated[index] = entry.copy(expanded = !entry.expanded)
                s.copy(entries = updated)
            }
        }
    }

    fun undoFile(path: String) {
        val change = _changedFiles.value.lastOrNull { it.path == path } ?: return
        scope.launch {
            _state.update { it.copy(status = "Mengembalikan $path...") }
            val result = executor.restoreContent(change.path, change.before)
            _changedFiles.update { list -> list.filterNot { it === change } }
            _state.update {
                it.copy(
                    entries = it.entries + ChatEntry.Agent("$result\nFile: ${change.path}"),
                    status = "",
                )
            }
        }
    }

    private suspend fun restoreSession(id: String) {
        val stored = store.loadHistory(id)
        if (stored.isNotEmpty()) {
            history.clear()
            history.addAll(stored)
        }
        val pending = store.loadPending(id)
        if (pending != null) {
            pendingToolCallId = pending.toolCallId
            questionGate.restorePending(pending.payload)
            _state.update {
                it.copy(
                    entries = entriesFromHistory(),
                    status = "Menunggu jawaban...",
                )
            }
        } else if (history.isNotEmpty() && _state.value.entries.isEmpty()) {
            _state.update { it.copy(entries = entriesFromHistory()) }
        }
    }

    private fun entriesFromHistory(): List<ChatEntry> = history.mapNotNull { message ->
        when (message) {
            is ChatMessage.User -> ChatEntry.User(message.content)
            is ChatMessage.Assistant -> message.content
                ?.takeIf { it.isNotBlank() }
                ?.let { ChatEntry.Agent(it) }
            else -> null
        }
    }

    private fun rememberMessage(message: ChatMessage) {
        history.add(message)
        val snapshot = history.toList()
        val id = sessionId
        scope.launch {
            if (id.isNotEmpty()) store.saveHistory(id, snapshot)
        }
    }

    private fun storeSavePending(pending: PendingQuestion?) {
        val id = sessionId
        scope.launch {
            if (id.isNotEmpty()) store.savePending(id, pending)
        }
    }

    private suspend fun executeTool(agent: Agent, call: ToolCall): String {
        if (call.name == "question") {
            _state.update { it.copy(status = "Menunggu jawaban...") }
            val items = try {
                QuestionParser.parse(call.arguments)
            } catch (e: Exception) {
                return e.message ?: "Argumen question tidak valid."
            }
            val payload = QuestionPayload(items)
            pendingToolCallId = call.id
            storeSavePending(PendingQuestion(call.id, payload))
            val answer = questionGate.ask(payload)
            storeSavePending(null)
            pendingToolCallId = null
            return answer ?: "User melewati pertanyaan."
        }
        val perm = agent.permissions[call.name] ?: Perm.DENY
        if (perm == Perm.DENY) {
            return "Agent \"${agent.name}\" tidak mengizinkan tool \"${call.name}\"."
        }
        if (perm == Perm.ALLOW) {
            _state.update { it.copy(status = "Menjalankan ${call.name}...") }
            return runTracked(call)
        }
        if (call.name == "write" || call.name == "edit") {
            _state.update { it.copy(status = "Memeriksa ${call.name}...") }
            val preview = executor.preview(call.name, call.arguments)
            if (preview is PreviewResult.Failed) return preview.message
            preview as PreviewResult.Ready
            val result = awaitPermission(call, preview.path, preview.isNewFile, preview.diff)
            return result
        }
        val path = try {
            org.json.JSONObject(call.arguments).optString("path", "").ifEmpty { "-" }
        } catch (e: Exception) {
            "-"
        }
        return awaitPermission(call, path, isNewFile = false, diff = null)
    }

    private suspend fun runTracked(call: ToolCall): String {
        if (call.name != "write" && call.name != "edit") {
            return executor.execute(call.name, call.arguments)
        }
        val before = executor.readContent(call.name, call.arguments)
        val expected = executor.expectedContent(call.name, call.arguments)
        val result = executor.execute(call.name, call.arguments)
        val now = executor.readContent(call.name, call.arguments)
        val path = try {
            org.json.JSONObject(call.arguments).optString("path", "")
        } catch (e: Exception) {
            ""
        }
        if (path.isNotEmpty() && now != null && expected != null && now == expected) {
            _changedFiles.update { list -> list.filterNot { it.path == path } + FileChange(path, before, now) }
        }
        return result
    }

    private suspend fun awaitPermission(
        call: ToolCall,
        path: String,
        isNewFile: Boolean,
        diff: DiffPreview?,
    ): String {
        val request = PermissionRequest(call.name, path, isNewFile, diff)
        _state.update { s ->
            s.copy(
                entries = s.entries + ChatEntry.Permission(request),
                status = "Menunggu persetujuan...",
            )
        }
        val decision = gate.request(request)
        resolvePermissionEntry(request, decision)
        if (decision == PermissionDecision.DENY) {
            return "User menolak izin ${call.name} untuk \"$path\"."
        }
        _state.update { it.copy(status = "Menjalankan ${call.name}...") }
        return runTracked(call)
    }

    private fun resolvePermissionEntry(request: PermissionRequest, decision: PermissionDecision) {
        val resolution = when (decision) {
            PermissionDecision.DENY -> PermissionResolution.DENIED
            PermissionDecision.ALLOW_ONCE -> PermissionResolution.GRANTED_ONCE
            PermissionDecision.ALLOW_SESSION -> PermissionResolution.GRANTED_SESSION
        }
        _state.update { s ->
            val index = s.entries.indexOfLast {
                it is ChatEntry.Permission && it.request == request && it.resolved == null
            }
            if (index < 0) {
                s
            } else {
                val updated = s.entries.toMutableList()
                updated[index] = (s.entries[index] as ChatEntry.Permission).copy(resolved = resolution)
                s.copy(entries = updated)
            }
        }
    }

    private fun interruptPendingPermissions() {
        _state.update { s ->
            if (s.entries.none { it is ChatEntry.Permission && it.resolved == null }) {
                s
            } else {
                val updated = s.entries.map { entry ->
                    if (entry is ChatEntry.Permission && entry.resolved == null) {
                        entry.copy(resolved = PermissionResolution.INTERRUPTED)
                    } else {
                        entry
                    }
                }
                s.copy(entries = updated)
            }
        }
    }

    private fun finishToolSteps() {
        _state.update { s ->
            if (s.entries.none { it is ChatEntry.ToolSteps && !it.done }) {
                s
            } else {
                val updated = s.entries.map { entry ->
                    if (entry is ChatEntry.ToolSteps && !entry.done) entry.copy(done = true) else entry
                }
                s.copy(entries = updated)
            }
        }
    }

    private suspend fun CoroutineScope.runLoop() {
        val agent = agents.activeAgent.first()
        var pendingResults: List<ToolCall> = emptyList()
        try {
            var finished = false
            var iteration = 1
            while (iteration <= MAX_ITERATIONS) {
                ensureActive()
                val turn = client.chat(
                    messages = listOf(ChatMessage.System(agent.systemPrompt)) + history,
                    tools = executor.specs(),
                ) { delta ->
                    _state.update {
                        it.copy(liveText = it.liveText + delta, status = "Menerima jawaban...")
                    }
                }

                if (turn.toolCalls.isEmpty()) {
                    rememberMessage(ChatMessage.Assistant(turn.content))
                    finished = true
                    break
                }

                rememberMessage(ChatMessage.Assistant(turn.content.ifEmpty { null }, turn.toolCalls))
                val pending = turn.toolCalls.toMutableList()
                pendingResults = pending

                val stepCalls = turn.toolCalls.filter { it.name in STEP_TOOLS }
                var stepsIndex: Int? = null
                if (stepCalls.isNotEmpty()) {
                    stepsIndex = _state.value.entries.size
                    _state.update { s ->
                        val steps = stepCalls.map { ChatEntry.ToolSteps.Step(it.tool, "") }
                        s.copy(entries = s.entries + ChatEntry.ToolSteps(steps))
                    }
                }

                for (call in turn.toolCalls) {
                    ensureActive()
                    val result = executeTool(agent, call)
                    rememberMessage(ChatMessage.ToolResult(call.id, result))
                    pending.remove(call)
                    if (call.name in STEP_TOOLS) {
                        val index = stepsIndex ?: continue
                        _state.update { s ->
                            val entry = s.entries.getOrNull(index)
                            if (entry !is ChatEntry.ToolSteps) {
                                s
                            } else {
                                val updated = s.entries.toMutableList()
                                updated[index] = entry.copy(
                                    steps = entry.steps.map { step ->
                                        if (step.result.isEmpty() && step.tool == call.name) {
                                            ChatEntry.ToolSteps.Step(step.tool, result)
                                        } else {
                                            step
                                        }
                                    },
                                )
                                s.copy(entries = updated)
                            }
                        }
                    }
                }

                finishToolSteps()
                _state.update { it.copy(status = "Melanjutkan...") }
                iteration++
            }

            commitLive()
            if (!finished) {
                _state.update {
                    it.copy(
                        entries = it.entries + ChatEntry.Agent(
                            "Berhenti: batas iterasi $MAX_ITERATIONS tercapai.",
                        ),
                    )
                }
            }
        } catch (e: CancellationException) {
            commitLive()
            throw e
        } catch (e: Exception) {
            commitLive()
            _state.update {
                it.copy(
                    entries = it.entries + ChatEntry.Error(
                        "Terjadi kesalahan: ${e.message ?: "tidak diketahui"}",
                    ),
                )
            }
        } finally {
            for (call in pendingResults) {
                rememberMessage(ChatMessage.ToolResult(call.id, "Dibatalkan."))
            }
            interruptPendingPermissions()
            finishToolSteps()
            questionGate.clearPending()
            storeSavePending(null)
            pendingToolCallId = null
            val id = sessionId
            scope.launch { if (id.isNotEmpty()) store.touch(id) }
            _state.update { it.copy(running = false, status = "") }
        }
    }

    private fun commitLive() {
        _state.update { s ->
            if (s.liveText.isBlank()) s
            else s.copy(entries = s.entries + ChatEntry.Agent(s.liveText), liveText = "")
        }
    }

    companion object {
        const val MAX_ITERATIONS = 25
        private val STEP_TOOLS = setOf("read", "list", "search")

        @Volatile
        private var instance: AgentSession? = null

        fun get(context: Context): AgentSession =
            instance ?: synchronized(this) {
                instance ?: AgentSession(context.applicationContext).also { instance = it }
            }
    }
}
