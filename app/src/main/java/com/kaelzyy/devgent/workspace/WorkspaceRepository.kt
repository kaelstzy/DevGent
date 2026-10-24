package com.kaelzyy.devgent.workspace

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.workspaceDataStore by preferencesDataStore(name = "workspace")

private const val GRANT_FLAGS =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

class WorkspaceRepository(private val context: Context) {

    val workspaceUri: Flow<Uri?> = context.workspaceDataStore.data.map { prefs ->
        prefs[KEY_URI]?.let(Uri::parse)?.takeIf { uri ->
            context.contentResolver.persistedUriPermissions.any { perm ->
                perm.uri == uri && perm.isReadPermission && perm.isWritePermission
            }
        }
    }

    suspend fun setWorkspace(uri: Uri) {
        val previous = context.workspaceDataStore.data.first()[KEY_URI]?.let(Uri::parse)
        if (previous != null && previous != uri) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(previous, GRANT_FLAGS)
            }
        }
        context.contentResolver.takePersistableUriPermission(uri, GRANT_FLAGS)
        context.workspaceDataStore.edit { it[KEY_URI] = uri.toString() }
    }

    suspend fun clearWorkspace() {
        val previous = context.workspaceDataStore.data.first()[KEY_URI]?.let(Uri::parse)
        if (previous != null) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(previous, GRANT_FLAGS)
            }
        }
        context.workspaceDataStore.edit { it.remove(KEY_URI) }
    }

    private companion object {
        val KEY_URI = stringPreferencesKey("workspace_tree_uri")
    }
}
