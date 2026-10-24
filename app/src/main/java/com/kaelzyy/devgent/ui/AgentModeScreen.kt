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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaelzyy.devgent.R
import com.kaelzyy.devgent.agent.Agent
import com.kaelzyy.devgent.agent.AgentRepository
import com.kaelzyy.devgent.agent.BUILT_IN_AGENTS
import com.kaelzyy.devgent.agent.Perm
import com.kaelzyy.devgent.agent.TOOL_NAMES
import com.kaelzyy.devgent.ui.theme.AccentBlue
import com.kaelzyy.devgent.ui.theme.AccentBlueLight
import com.kaelzyy.devgent.ui.theme.Card
import com.kaelzyy.devgent.ui.theme.DiffDeleteText
import kotlinx.coroutines.launch

private data class AgentEditor(
    val id: String,
    val name: String,
    val description: String,
    val prompt: String,
    val permissions: Map<String, Perm>,
)

private fun Agent.toEditor() = AgentEditor(
    id = id,
    name = name,
    description = description,
    prompt = systemPrompt,
    permissions = TOOL_NAMES.associateWith { permissions[it] ?: Perm.ASK },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentModeScreen(
    repository: AgentRepository,
    activeAgent: Agent,
    onAgentSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    val agents by repository.agents.collectAsState(initial = BUILT_IN_AGENTS)
    val scope = rememberCoroutineScope()
    var editor by remember { mutableStateOf<AgentEditor?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (editor == null) {
                TopAppBar(
                    title = { Text(stringResource(R.string.agent_mode_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.nav_back),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(
                                if (agents.none { it.id == editor!!.id }) R.string.new_agent_title
                                else R.string.edit_agent,
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { editor = null; error = null }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.nav_back),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            }
        },
    ) { innerPadding ->
        val current = editor
        if (current == null) {
            AgentList(
                modifier = Modifier.padding(innerPadding),
                agents = agents,
                activeId = activeAgent.id,
                onSelect = onAgentSelected,
                onEdit = { editor = it.toEditor(); error = null },
                onDelete = { agent ->
                    scope.launch { repository.deleteCustom(agent.id) }
                },
                onClone = { agent ->
                    scope.launch {
                        var candidate = "${agent.name} (salinan)"
                        var n = 2
                        while (repository.isNameTaken(candidate)) {
                            candidate = "${agent.name} (salinan $n)"
                            n++
                        }
                        val clone = Agent(
                            id = "custom_${System.currentTimeMillis()}",
                            name = candidate,
                            description = agent.description,
                            systemPrompt = agent.systemPrompt,
                            permissions = agent.permissions,
                            builtIn = false,
                        )
                        repository.saveCustom(clone)
                        editor = clone.toEditor()
                        error = null
                    }
                },
                onCreate = {
                    editor = AgentEditor(
                        id = "custom_${System.currentTimeMillis()}",
                        name = "",
                        description = "",
                        prompt = "",
                        permissions = TOOL_NAMES.associateWith {
                            if (it == "write" || it == "edit") Perm.ASK else Perm.ALLOW
                        },
                    )
                    error = null
                },
            )
        } else {
            AgentEditorForm(
                modifier = Modifier.padding(innerPadding),
                editor = current,
                error = error,
                onEditorChange = { editor = it },
                onCancel = { editor = null; error = null },
                onSave = {
                    scope.launch {
                        val name = current.name.trim()
                        val prompt = current.prompt.trim()
                        error = when {
                            name.isEmpty() -> "Nama wajib diisi."
                            prompt.isEmpty() -> "System prompt wajib diisi."
                            repository.isNameTaken(name, excludeId = current.id) ->
                                "Nama sudah dipakai."
                            else -> null
                        }
                        if (error == null) {
                            repository.saveCustom(
                                Agent(
                                    id = current.id,
                                    name = name,
                                    description = current.description.trim(),
                                    systemPrompt = prompt,
                                    permissions = current.permissions,
                                    builtIn = false,
                                ),
                            )
                            editor = null
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun AgentList(
    modifier: Modifier,
    agents: List<Agent>,
    activeId: String,
    onSelect: (String) -> Unit,
    onEdit: (Agent) -> Unit,
    onDelete: (Agent) -> Unit,
    onClone: (Agent) -> Unit,
    onCreate: () -> Unit,
) {
    val builtIns = agents.filter { it.builtIn }
    val customs = agents.filterNot { it.builtIn }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionLabel(stringResource(R.string.agents_builtin))
        }
        items(builtIns, key = { it.id }) { agent ->
            AgentRow(
                agent = agent,
                isActive = agent.id == activeId,
                onSelect = onSelect,
                onEdit = null,
                onDelete = null,
                onClone = onClone,
            )
        }
        item {
            SectionLabel(stringResource(R.string.agents_custom))
        }
        if (customs.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.agents_custom_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(customs, key = { it.id }) { agent ->
                AgentRow(
                    agent = agent,
                    isActive = agent.id == activeId,
                    onSelect = onSelect,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onClone = null,
                )
            }
        }
        item {
            OutlinedButton(
                onClick = onCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(stringResource(R.string.create_agent))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AgentRow(
    agent: Agent,
    isActive: Boolean,
    onSelect: (String) -> Unit,
    onEdit: ((Agent) -> Unit)?,
    onDelete: ((Agent) -> Unit)?,
    onClone: ((Agent) -> Unit)?,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = 1.dp,
                color = if (isActive) AccentBlueLight else MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .clickable { onSelect(agent.id) },
        color = if (isActive) Card else MaterialTheme.colorScheme.surface,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = agent.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.active_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentBlueLight,
                        )
                    }
                }
                Text(
                    text = agent.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            onClone?.let {
                IconButton(onClick = { it(agent) }) {
                    CopyGlyph(tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            onEdit?.let {
                IconButton(onClick = { it(agent) }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_agent),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            onDelete?.let {
                IconButton(onClick = { it(agent) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_agent),
                        tint = DiffDeleteText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun CopyGlyph(tint: Color) {
    Box(modifier = Modifier.size(20.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(tint),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, tint, RoundedCornerShape(3.dp)),
        )
    }
}

@Composable
private fun AgentEditorForm(
    modifier: Modifier,
    editor: AgentEditor,
    error: String?,
    onEditorChange: (AgentEditor) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = editor.name,
            onValueChange = { onEditorChange(editor.copy(name = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.agent_name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            colors = formColors(),
        )
        OutlinedTextField(
            value = editor.description,
            onValueChange = { onEditorChange(editor.copy(description = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.agent_desc_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = formColors(),
        )
        OutlinedTextField(
            value = editor.prompt,
            onValueChange = { onEditorChange(editor.copy(prompt = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.agent_prompt_label)) },
            minLines = 6,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = formColors(),
        )
        SectionLabel(stringResource(R.string.agent_permissions_label))
        TOOL_NAMES.forEach { tool ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tool,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                )
                PermButton(
                    label = stringResource(R.string.perm_allow),
                    selected = editor.permissions[tool] == Perm.ALLOW,
                ) {
                    onEditorChange(
                        editor.copy(permissions = editor.permissions + (tool to Perm.ALLOW)),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                PermButton(
                    label = stringResource(R.string.perm_ask),
                    selected = editor.permissions[tool] == Perm.ASK,
                ) {
                    onEditorChange(
                        editor.copy(permissions = editor.permissions + (tool to Perm.ASK)),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                PermButton(
                    label = stringResource(R.string.perm_deny),
                    selected = editor.permissions[tool] == Perm.DENY,
                ) {
                    onEditorChange(
                        editor.copy(permissions = editor.permissions + (tool to Perm.DENY)),
                    )
                }
            }
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = DiffDeleteText,
            )
        }
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
        ) {
            Text(stringResource(R.string.save_agent))
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
        ) {
            Text(
                text = stringResource(R.string.cancel),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) AccentBlue else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) AccentBlue else MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun formColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentBlueLight,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedTextColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
    focusedLabelColor = AccentBlueLight,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = AccentBlue,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
)
