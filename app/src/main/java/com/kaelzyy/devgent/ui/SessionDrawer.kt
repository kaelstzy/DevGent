package com.kaelzyy.devgent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaelzyy.devgent.R
import com.kaelzyy.devgent.agent.FileChange
import com.kaelzyy.devgent.agent.SessionMeta
import com.kaelzyy.devgent.ui.theme.AccentBlue
import com.kaelzyy.devgent.ui.theme.AccentBlueLight
import com.kaelzyy.devgent.ui.theme.Card
import com.kaelzyy.devgent.ui.theme.DiffDeleteText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun SessionDrawer(
    sessions: List<SessionMeta>,
    activeSessionId: String,
    changedFiles: List<FileChange>,
    onNewChat: () -> Unit,
    onSelectSession: (String) -> Unit,
    onOpenAgents: () -> Unit,
    onOpenSettings: () -> Unit,
    onUndoFile: (String) -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.width(316.dp),
        drawerContainerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppLogo()
                Text(
                    text = "DevGent",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            OutlinedButton(
                onClick = onNewChat,
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
                Text(stringResource(R.string.chat_new))
            }

            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            val groups = listOf(
                stringResource(R.string.drawer_today) to sessions.filter {
                    it.updatedAtDate() == today
                },
                stringResource(R.string.drawer_yesterday) to sessions.filter {
                    it.updatedAtDate() == yesterday
                },
                stringResource(R.string.drawer_older) to sessions.filter {
                    val date = it.updatedAtDate()
                    date.isBefore(yesterday)
                },
            )
            for ((label, items) in groups) {
                if (items.isEmpty()) continue
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                for (session in items) {
                    SessionRow(
                        session = session,
                        isActive = session.id == activeSessionId,
                        onClick = { onSelectSession(session.id) },
                    )
                }
            }

            if (changedFiles.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Text(
                    text = stringResource(R.string.changed_files),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (change in changedFiles) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = change.path,
                            modifier = Modifier.weight(1f),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { onUndoFile(change.path) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.undo_file),
                                tint = AccentBlueLight,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.size(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            DrawerFooterRow(
                icon = Icons.Default.Build,
                label = stringResource(R.string.drawer_agents),
                onClick = onOpenAgents,
            )
            DrawerFooterRow(
                icon = Icons.Default.Settings,
                label = stringResource(R.string.drawer_settings),
                onClick = onOpenSettings,
            )
        }
    }
}

private fun SessionMeta.updatedAtDate(): LocalDate =
    Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
fun AppLogo(size: Int = 36) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AccentBlue),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "D",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SessionRow(
    session: SessionMeta,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isActive) Card else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isActive) MaterialTheme.colorScheme.outline else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = session.title.ifEmpty { stringResource(R.string.chat_new) },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isActive) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = AccentBlueLight,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun DrawerFooterRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
