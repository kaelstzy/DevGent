package com.kaelzyy.devgent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaelzyy.devgent.R
import com.kaelzyy.devgent.agent.ChatEntry
import com.kaelzyy.devgent.agent.ChatState
import com.kaelzyy.devgent.agent.FileChange
import com.kaelzyy.devgent.agent.PermissionDecision
import com.kaelzyy.devgent.agent.PermissionResolution
import com.kaelzyy.devgent.agent.QuestionPayload
import com.kaelzyy.devgent.agent.SessionMeta
import com.kaelzyy.devgent.tools.DiffLineType
import com.kaelzyy.devgent.tools.DiffPreview
import com.kaelzyy.devgent.ui.theme.AccentBlue
import com.kaelzyy.devgent.ui.theme.AccentBlueLight
import com.kaelzyy.devgent.ui.theme.Card
import com.kaelzyy.devgent.ui.theme.DiffAddBackground
import com.kaelzyy.devgent.ui.theme.DiffAddText
import com.kaelzyy.devgent.ui.theme.DiffDeleteBackground
import com.kaelzyy.devgent.ui.theme.DiffDeleteText
import kotlinx.coroutines.launch

private val MessageStyle = TextStyle(fontSize = 15.5.sp, lineHeight = 24.8.sp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatState,
    folderName: String?,
    agentName: String,
    pendingQuestion: QuestionPayload?,
    sessions: List<SessionMeta>,
    activeSessionId: String,
    changedFiles: List<FileChange>,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectFolder: () -> Unit,
    onOpenAgents: () -> Unit,
    onRespondPermission: (PermissionDecision) -> Unit,
    onAnswerQuestion: (String?) -> Unit,
    onNewChat: () -> Unit,
    onSelectSession: (String) -> Unit,
    onUndoFile: (String) -> Unit,
    onToggleToolSteps: (Int) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val totalCount = state.entries.size + if (state.liveText.isEmpty()) 0 else 1
    LaunchedEffect(state.entries.size, state.liveText.length) {
        if (totalCount > 0) listState.scrollToItem(totalCount - 1)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionDrawer(
                sessions = sessions,
                activeSessionId = activeSessionId,
                changedFiles = changedFiles,
                onNewChat = {
                    onNewChat()
                    scope.launch { drawerState.close() }
                },
                onSelectSession = { id ->
                    onSelectSession(id)
                    scope.launch { drawerState.close() }
                },
                onOpenAgents = {
                    onOpenAgents()
                    scope.launch { drawerState.close() }
                },
                onOpenSettings = {
                    onOpenSettings()
                    scope.launch { drawerState.close() }
                },
                onUndoFile = onUndoFile,
            )
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = stringResource(R.string.menu_cd),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                FolderChip(
                                    label = folderName ?: stringResource(R.string.pick_folder),
                                    active = folderName != null,
                                    onClick = onSelectFolder,
                                )
                            }
                            IconButton(onClick = onNewChat) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.chat_new),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            },
            bottomBar = {
                if (pendingQuestion != null) {
                    QuestionPanel(
                        payload = pendingQuestion,
                        onSubmit = { answer -> onAnswerQuestion(answer) },
                        onSkip = { onAnswerQuestion(null) },
                    )
                } else {
                    Composer(
                        value = input,
                        onValueChange = { input = it },
                        running = state.running,
                        agentName = agentName,
                        onOpenAgents = onOpenAgents,
                        onSend = {
                            onSend(input)
                            input = ""
                        },
                        onStop = onStop,
                    )
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (totalCount == 0) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Beranda(
                            folderName = folderName,
                            onSelectFolder = onSelectFolder,
                            onSuggestion = onSend,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(totalCount) { index ->
                            if (index < state.entries.size) {
                                EntryView(
                                    entry = state.entries[index],
                                    index = index,
                                    onRespondPermission = onRespondPermission,
                                    onToggleSteps = onToggleToolSteps,
                                )
                            } else {
                                AgentText(text = state.liveText)
                            }
                        }
                    }
                    if (state.running && state.status.isNotEmpty()) {
                        StatusRow(status = state.status)
                    }
                }
            }
        }
    }
}

@Composable
private fun Beranda(
    folderName: String?,
    onSelectFolder: () -> Unit,
    onSuggestion: (String) -> Unit,
) {
    if (folderName == null) {
        EmptyWorkspace(onSelectFolder = onSelectFolder)
    } else {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppLogo(size = 48)
            Text(
                text = stringResource(R.string.greeting_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.greeting_sub, folderName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.size(8.dp))
            listOf(
                stringResource(R.string.suggestion_1),
                stringResource(R.string.suggestion_2),
                stringResource(R.string.suggestion_3),
            ).forEach { suggestion ->
                OutlinedButton(
                    onClick = { onSuggestion(suggestion) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyWorkspace(onSelectFolder: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onSelectFolder) {
            Text(stringResource(R.string.pick_folder))
        }
    }
}

@Composable
private fun EntryView(
    entry: ChatEntry,
    index: Int,
    onRespondPermission: (PermissionDecision) -> Unit,
    onToggleSteps: (Int) -> Unit,
) {
    when (entry) {
        is ChatEntry.User -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 300.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    text = entry.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MessageStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        is ChatEntry.Agent -> AgentText(text = entry.text)
        is ChatEntry.ToolSteps -> ToolStepsCard(
            entry = entry,
            onToggle = { onToggleSteps(index) },
        )
        is ChatEntry.Error -> Text(
            text = entry.text,
            modifier = Modifier.fillMaxWidth(),
            style = MessageStyle,
            color = DiffDeleteText,
        )
        is ChatEntry.Permission -> PermissionCard(
            entry = entry,
            onRespond = onRespondPermission,
        )
    }
}

@Composable
private fun AgentText(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MessageStyle,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun StatusRow(status: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.5.dp,
            color = AccentBlueLight,
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolStepsCard(
    entry: ChatEntry.ToolSteps,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (entry.done) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = DiffAddText,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = AccentBlueLight,
                )
            }
            Text(
                text = entry.label.ifEmpty { stringResource(R.string.tool_steps_running) },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        rotationZ = if (entry.expanded) 180f else 0f
                    },
            )
        }
        if (entry.expanded && entry.steps.isNotEmpty()) {
            Spacer(modifier = Modifier.size(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                entry.steps.forEach { step ->
                    Text(
                        text = "[${step.tool}] ${step.result.take(2000)}",
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    entry: ChatEntry.Permission,
    onRespond: (PermissionDecision) -> Unit,
) {
    val request = entry.request
    val diff = request.diff
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Card)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when {
                    diff == null -> request.tool.uppercase()
                    request.isNewFile -> stringResource(R.string.permission_header_create)
                    else -> stringResource(R.string.permission_header_edit)
                },
                style = MaterialTheme.typography.labelSmall,
                color = AccentBlueLight,
            )
            Text(
                text = request.path,
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                maxLines = 2,
            )
            if (diff != null) {
                Text(
                    text = stringResource(R.string.diff_stats, diff.added, diff.removed),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (diff.removed > 0) DiffDeleteText else DiffAddText,
                )
            }
        }
        if (diff != null) {
            DiffBlock(diff = diff)
        }
        when (entry.resolved) {
            null -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onRespond(PermissionDecision.DENY) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.deny))
                    }
                    Button(
                        onClick = { onRespond(PermissionDecision.ALLOW_ONCE) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    ) {
                        Text(stringResource(R.string.allow_once))
                    }
                }
                TextButton(
                    onClick = { onRespond(PermissionDecision.ALLOW_SESSION) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.allow_session),
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentBlueLight,
                    )
                }
            }
            PermissionResolution.DENIED -> Text(
                text = stringResource(R.string.permission_denied),
                style = MaterialTheme.typography.labelMedium,
                color = DiffDeleteText,
            )
            PermissionResolution.GRANTED_ONCE -> Text(
                text = stringResource(R.string.permission_granted),
                style = MaterialTheme.typography.labelMedium,
                color = DiffAddText,
            )
            PermissionResolution.GRANTED_SESSION -> Text(
                text = stringResource(R.string.permission_granted_session),
                style = MaterialTheme.typography.labelMedium,
                color = DiffAddText,
            )
            PermissionResolution.INTERRUPTED -> Text(
                text = stringResource(R.string.permission_interrupted),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiffBlock(diff: DiffPreview) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (diff.lines.isEmpty()) {
            Text(
                text = stringResource(R.string.diff_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                diff.lines.forEach { line ->
                    val background = when (line.type) {
                        DiffLineType.ADD -> DiffAddBackground
                        DiffLineType.REMOVE -> DiffDeleteBackground
                        DiffLineType.CONTEXT -> Color.Transparent
                    }
                    val textColor = when (line.type) {
                        DiffLineType.ADD -> DiffAddText
                        DiffLineType.REMOVE -> DiffDeleteText
                        DiffLineType.CONTEXT -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val sign = when (line.type) {
                        DiffLineType.ADD -> "+"
                        DiffLineType.REMOVE -> "−"
                        DiffLineType.CONTEXT -> " "
                    }
                    Text(
                        text = "$sign ${line.text}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(background)
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            color = textColor,
                        ),
                    )
                }
            }
            if (diff.truncatedCount > 0) {
                Text(
                    text = stringResource(R.string.diff_truncated, diff.truncatedCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FolderGlyph() {
    Box(modifier = Modifier.size(16.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(11.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AccentBlueLight),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 1.dp)
                .width(7.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                .background(AccentBlueLight),
        )
    }
}

@Composable
private fun FolderChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = if (active) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (active) {
                FolderGlyph()
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    running: Boolean,
    agentName: String,
    onOpenAgents: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Column {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.5.sp,
                    lineHeight = 24.8.sp,
                ),
                maxLines = 6,
                cursorBrush = SolidColor(AccentBlue),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.composer_placeholder),
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.5.sp,
                                lineHeight = 24.8.sp,
                            ),
                        )
                    }
                    innerTextField()
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeChip(label = agentName, onClick = onOpenAgents)
                Spacer(modifier = Modifier.weight(1f))
                SendStopButton(
                    running = running,
                    enabled = value.isNotBlank(),
                    onSend = onSend,
                    onStop = onStop,
                )
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = AccentBlueLight,
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.agent_mode_title),
                tint = AccentBlueLight,
            )
        }
    }
}

@Composable
private fun QuestionPanel(
    payload: QuestionPayload,
    onSubmit: (String) -> Unit,
    onSkip: () -> Unit,
) {
    val selections = remember(payload) { mutableStateMapOf<Int, Set<Int>>() }
    val customTexts = remember(payload) { mutableStateMapOf<Int, String>() }
    val customActive = remember(payload) { mutableStateSetOf<Int>() }
    val scrollState = rememberScrollState()
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(shape)
            .background(Card)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.question_label),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = AccentBlueLight,
            )
            payload.items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
                Text(
                    text = item.question,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                item.options.forEachIndexed { optionIndex, option ->
                    OptionRow(
                        number = optionIndex + 1,
                        label = option,
                        selected = optionIndex in selections[index].orEmpty(),
                    ) {
                        val current = selections[index].orEmpty()
                        selections[index] = if (item.multiSelect) {
                            if (optionIndex in current) current - optionIndex else current + optionIndex
                        } else {
                            if (current == setOf(optionIndex)) emptySet() else setOf(optionIndex)
                        }
                    }
                }
                if (item.allowCustom) {
                    if (index !in customActive) {
                        TextButton(
                            onClick = { customActive.add(index) },
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.custom_option),
                                style = MaterialTheme.typography.labelLarge,
                                color = AccentBlueLight,
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = customTexts[index].orEmpty(),
                            onValueChange = { customTexts[index] = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.answer_placeholder)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentBlueLight,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                cursorColor = AccentBlue,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.skip_question))
                }
                Button(
                    onClick = {
                        val answer = payload.items.mapIndexedNotNull { index, item ->
                            val chosen = selections[index].orEmpty().map { item.options[it] }
                            val custom = customTexts[index]?.trim().orEmpty()
                            val parts = chosen + custom.takeIf { it.isNotEmpty() }
                            if (parts.isEmpty()) null
                            else "\"${item.question}\" → ${parts.joinToString("; ")}"
                        }.joinToString("\n")
                        if (answer.isEmpty()) onSkip() else onSubmit(answer)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                ) {
                    Text(stringResource(R.string.send_answer))
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    number: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) AccentBlueLight else MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$number.",
            modifier = Modifier.width(24.dp),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = if (selected) AccentBlueLight else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun SendStopButton(
    running: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val background = if (running) MaterialTheme.colorScheme.outline else AccentBlue
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = running || enabled) {
                if (running) onStop() else onSend()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (running) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onBackground),
            )
        } else {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = stringResource(
                    if (running) R.string.stop_agent else R.string.send_message,
                ),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
