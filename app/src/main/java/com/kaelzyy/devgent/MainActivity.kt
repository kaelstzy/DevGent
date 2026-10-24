package com.kaelzyy.devgent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import com.kaelzyy.devgent.agent.AgentRepository
import com.kaelzyy.devgent.agent.AgentSession
import com.kaelzyy.devgent.agent.BUILT_IN_AGENTS
import com.kaelzyy.devgent.llm.LlmClient
import com.kaelzyy.devgent.settings.SettingsRepository
import com.kaelzyy.devgent.ui.AgentModeScreen
import com.kaelzyy.devgent.ui.ChatScreen
import com.kaelzyy.devgent.ui.SettingsScreen
import com.kaelzyy.devgent.ui.theme.DevGentTheme
import com.kaelzyy.devgent.workspace.WorkspaceRepository
import kotlinx.coroutines.launch

enum class AppScreen { MAIN, SETTINGS, AGENTS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DevGentTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    var screen by rememberSaveable { mutableStateOf(AppScreen.MAIN) }

    when (screen) {
        AppScreen.MAIN -> MainRoute(
            onOpenSettings = { screen = AppScreen.SETTINGS },
            onOpenAgents = { screen = AppScreen.AGENTS },
        )
        AppScreen.SETTINGS -> {
            BackHandler { screen = AppScreen.MAIN }
            SettingsRoute(onBack = { screen = AppScreen.MAIN })
        }
        AppScreen.AGENTS -> {
            BackHandler { screen = AppScreen.MAIN }
            AgentsRoute(onBack = { screen = AppScreen.MAIN })
        }
    }
}

@Composable
private fun MainRoute(onOpenSettings: () -> Unit, onOpenAgents: () -> Unit) {
    val context = LocalContext.current
    val workspace = remember { WorkspaceRepository(context.applicationContext) }
    val session = remember { AgentSession.get(context) }
    val scope = rememberCoroutineScope()

    val state by session.state.collectAsState()
    val workspaceUri by workspace.workspaceUri.collectAsState(initial = null)
    val activeAgent by session.activeAgent.collectAsState(initial = BUILT_IN_AGENTS.first())
    val pendingQuestion by session.pendingQuestion.collectAsState()
    val sessions by session.sessions.collectAsState(initial = emptyList())
    val activeSessionId by session.activeSessionId.collectAsState()
    val changedFiles by session.changedFiles.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            scope.launch { runCatching { workspace.setWorkspace(uri) } }
        }
    }

    val folderName = workspaceUri
        ?.let { DocumentFile.fromTreeUri(context, it)?.name }

    ChatScreen(
        state = state,
        folderName = folderName,
        agentName = activeAgent.name,
        pendingQuestion = pendingQuestion,
        sessions = sessions,
        activeSessionId = activeSessionId,
        changedFiles = changedFiles,
        onSend = session::send,
        onStop = session::stop,
        onOpenSettings = onOpenSettings,
        onSelectFolder = { launcher.launch(workspaceUri) },
        onOpenAgents = onOpenAgents,
        onRespondPermission = session::respondPermission,
        onAnswerQuestion = session::respondQuestion,
        onNewChat = session::newChat,
        onSelectSession = session::openSession,
        onUndoFile = session::undoFile,
        onToggleToolSteps = session::toggleToolSteps,
        onDeleteEntry = session::deleteFrom,
        onEditEntry = session::editAndResend,
    )
}

@Composable
private fun AgentsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { AgentRepository(context.applicationContext) }
    val session = remember { AgentSession.get(context) }
    val activeAgent by repository.activeAgent.collectAsState(initial = BUILT_IN_AGENTS.first())

    AgentModeScreen(
        repository = repository,
        activeAgent = activeAgent,
        onAgentSelected = { id -> session.switchAgent(id) },
        onBack = onBack,
    )
}

@Composable
private fun SettingsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context.applicationContext) }
    val client = remember { LlmClient(settingsRepository) }

    SettingsScreen(
        repository = settingsRepository,
        client = client,
        onBack = onBack,
    )
}
