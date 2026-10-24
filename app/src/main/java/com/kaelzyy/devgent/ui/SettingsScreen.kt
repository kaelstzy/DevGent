package com.kaelzyy.devgent.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kaelzyy.devgent.R
import com.kaelzyy.devgent.llm.LlmClient
import com.kaelzyy.devgent.llm.Providers
import com.kaelzyy.devgent.settings.AppSettings
import com.kaelzyy.devgent.settings.SettingsRepository
import com.kaelzyy.devgent.ui.theme.DiffAddText
import com.kaelzyy.devgent.ui.theme.DiffDeleteText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    client: LlmClient,
    onBack: () -> Unit,
) {
    val settings by repository.appSettings.collectAsState(initial = AppSettings.DEFAULT)
    val scope = rememberCoroutineScope()

    var keyInput by rememberSaveable { mutableStateOf("") }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var keyMessage by rememberSaveable { mutableStateOf("") }
    var keyMessageOk by rememberSaveable { mutableStateOf(true) }
    var testDone by rememberSaveable { mutableStateOf(false) }
    var testOk by rememberSaveable { mutableStateOf(false) }
    var testMessage by rememberSaveable { mutableStateOf("") }
    var testLoading by rememberSaveable { mutableStateOf(false) }
    var providerMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var reasoningMenu by remember { mutableStateOf(false) }

    val preset = Providers.byId(settings.provider)
    val reasoningLabel = when (settings.reasoning) {
        "minimal" -> stringResource(R.string.reasoning_minimal)
        "low" -> stringResource(R.string.reasoning_low)
        "medium" -> stringResource(R.string.reasoning_medium)
        "high" -> stringResource(R.string.reasoning_high)
        else -> stringResource(R.string.reasoning_off)
    }

    val msgKeyOk = stringResource(R.string.key_save_ok)
    val msgKeyFail = stringResource(R.string.key_save_fail)
    val msgKeyDeleteOk = stringResource(R.string.key_delete_ok)
    val msgTestOk = stringResource(R.string.test_ok)

    LaunchedEffect(settings.baseUrl, settings.model) {
        if (baseUrl.isEmpty()) baseUrl = settings.baseUrl
        if (model.isEmpty()) model = settings.model
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box {
                OutlinedTextField(
                    value = preset.label,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.provider_label)) },
                    trailingIcon = {
                        IconButton(onClick = { providerMenu = !providerMenu }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    singleLine = true,
                )
                DropdownMenu(
                    expanded = providerMenu,
                    onDismissRequest = { providerMenu = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Providers.PRESETS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                providerMenu = false
                                scope.launch { repository.saveProvider(option.id) }
                                if (option.baseUrl.isNotEmpty()) {
                                    baseUrl = option.baseUrl
                                    scope.launch { repository.saveBaseUrl(option.baseUrl) }
                                }
                                if (option.models.isNotEmpty()) {
                                    model = option.models.first()
                                    scope.launch { repository.saveModel(option.models.first()) }
                                }
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.api_key_label)) },
                placeholder = {
                    Text(
                        stringResource(
                            if (settings.hasApiKey) R.string.api_key_saved_placeholder
                            else R.string.api_key_empty_placeholder,
                        ),
                    )
                },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) {
                        Text(
                            stringResource(if (showKey) R.string.hide_key else R.string.show_key),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
            )

            if (settings.hasApiKey && keyInput.isEmpty()) {
                Text(
                    text = stringResource(R.string.key_stored_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val result = runCatching { repository.saveApiKey(keyInput) }
                            result.fold(
                                onSuccess = {
                                    keyInput = ""
                                    keyMessageOk = true
                                    keyMessage = msgKeyOk
                                },
                                onFailure = { e ->
                                    keyMessageOk = false
                                    keyMessage = String.format(
                                        msgKeyFail,
                                        e.message ?: "kesalahan tidak diketahui",
                                    )
                                },
                            )
                        }
                    },
                    enabled = keyInput.isNotBlank() && !testLoading,
                ) {
                    Text(stringResource(R.string.save_key))
                }
                if (settings.hasApiKey) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                repository.clearApiKey()
                                keyInput = ""
                                keyMessageOk = true
                                keyMessage = msgKeyDeleteOk
                            }
                        },
                    ) {
                        Text(stringResource(R.string.delete_key))
                    }
                }
            }

            if (keyMessage.isNotEmpty()) {
                Text(
                    text = keyMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (keyMessageOk) DiffAddText else DiffDeleteText,
                )
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = {
                    baseUrl = it
                    scope.launch { repository.saveBaseUrl(it) }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.base_url_label)) },
                placeholder = { Text(stringResource(R.string.base_url_hint)) },
                supportingText = { Text(stringResource(R.string.base_url_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )

            Box {
                OutlinedTextField(
                    value = model,
                    onValueChange = {
                        model = it
                        scope.launch { repository.saveModel(it) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.model_label)) },
                    placeholder = { Text(stringResource(R.string.model_hint)) },
                    supportingText = { Text(stringResource(R.string.model_hint)) },
                    singleLine = true,
                    trailingIcon = {
                        if (preset.models.isNotEmpty()) {
                            IconButton(onClick = { modelMenu = !modelMenu }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )
                if (preset.models.isNotEmpty()) {
                    DropdownMenu(
                        expanded = modelMenu,
                        onDismissRequest = { modelMenu = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        preset.models.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                onClick = {
                                    modelMenu = false
                                    model = option
                                    scope.launch { repository.saveModel(option) }
                                },
                            )
                        }
                    }
                }
            }

            if (preset.supportsReasoning) {
                Box {
                    OutlinedTextField(
                        value = reasoningLabel,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.reasoning_label)) },
                        trailingIcon = {
                            IconButton(onClick = { reasoningMenu = !reasoningMenu }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        singleLine = true,
                    )
                    DropdownMenu(
                        expanded = reasoningMenu,
                        onDismissRequest = { reasoningMenu = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Providers.REASONING_LEVELS.forEach { level ->
                            val label = when (level) {
                                "minimal" -> stringResource(R.string.reasoning_minimal)
                                "low" -> stringResource(R.string.reasoning_low)
                                "medium" -> stringResource(R.string.reasoning_medium)
                                "high" -> stringResource(R.string.reasoning_high)
                                else -> stringResource(R.string.reasoning_off)
                            }
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    reasoningMenu = false
                                    scope.launch { repository.saveReasoning(level) }
                                },
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        testLoading = true
                        testDone = false
                        scope.launch {
                            val result = runCatching { client.testConnection() }
                            testLoading = false
                            testDone = true
                            testOk = result.isSuccess
                            testMessage = if (result.isSuccess) msgTestOk
                            else result.exceptionOrNull()?.message ?: ""
                        }
                    },
                    enabled = !testLoading,
                ) {
                    if (testLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.test_connection))
                    }
                }
            }

            if (testDone) {
                Text(
                    text = if (testOk) testMessage
                    else stringResource(R.string.test_failed, testMessage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (testOk) DiffAddText else DiffDeleteText,
                )
            }

            PrivacyNote()
        }
    }
}

@Composable
private fun PrivacyNote() {
    val shape = RoundedCornerShape(14)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = stringResource(R.string.privacy_note),
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
