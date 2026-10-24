package com.kaelzyy.devgent.tools

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.kaelzyy.devgent.llm.ToolSpec
import com.kaelzyy.devgent.workspace.WorkspacePath
import com.kaelzyy.devgent.workspace.WorkspacePathException
import com.kaelzyy.devgent.workspace.WorkspaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class ToolExecutor(
    private val context: Context,
    private val workspace: WorkspaceRepository,
    private val fileTools: FileTools,
) {

    suspend fun execute(name: String, arguments: String): String =
        runCatching { dispatch(name, arguments) }
            .getOrElse { e ->
                if (e is CancellationException) throw e
                e.userMessage()
            }

    suspend fun preview(name: String, arguments: String): PreviewResult =
        runCatching { dispatchPreview(name, arguments) }
            .getOrElse { e ->
                if (e is CancellationException) throw e
                PreviewResult.Failed(e.userMessage())
            }

    suspend fun readContent(name: String, arguments: String): String? =
        runCatching {
            val root = resolveRoot()
            val path = JSONObject(arguments).optString("path", "")
            if (path.isEmpty()) null else fileTools.readRawOrNull(root, path)
        }.getOrNull()

    suspend fun expectedContent(name: String, arguments: String): String? =
        runCatching {
            val args = JSONObject(arguments)
            when (name) {
                "write" -> args.optString("content")
                "edit" -> {
                    val path = args.optString("path", "")
                    val current = fileTools.readRawOrNull(resolveRoot(), path) ?: return@runCatching null
                    val oldStr = args.optString("old_str", "")
                    val newStr = args.optString("new_str", "")
                    if (!current.contains(oldStr)) null else current.replace(oldStr, newStr)
                }
                else -> null
            }
        }.getOrNull()

    suspend fun restoreContent(path: String, before: String?): String {
        val root = resolveRoot()
        return fileTools.restore(root, path, before)
    }

    private suspend fun dispatchPreview(name: String, arguments: String): PreviewResult {
        val args = try {
            if (arguments.isBlank()) JSONObject() else JSONObject(arguments)
        } catch (e: JSONException) {
            throw WorkspacePathException("Argumen bukan JSON valid: ${e.message}")
        }
        val root = resolveRoot()
        return when (name) {
            "write" -> {
                val path = requireString(args, "path")
                val content = requireString(args, "content")
                val target = WorkspacePath.resolveParent(root, path, createMissing = false)
                val isNew = target.parent.findFile(target.name) == null
                PreviewResult.Ready(path, isNew, fileTools.previewWrite(root, path, content))
            }
            "edit" -> PreviewResult.Ready(
                path = requireString(args, "path"),
                isNewFile = false,
                diff = fileTools.previewEdit(
                    root = root,
                    path = requireString(args, "path"),
                    oldStr = requireString(args, "old_str"),
                    newStr = requireString(args, "new_str"),
                ),
            )
            else -> PreviewResult.Failed("Tool \"$name\" tidak butuh persetujuan.")
        }
    }

    private suspend fun dispatch(name: String, arguments: String): String {
        val args = try {
            if (arguments.isBlank()) JSONObject() else JSONObject(arguments)
        } catch (e: JSONException) {
            throw WorkspacePathException("Argumen bukan JSON valid: ${e.message}")
        }
        val root = resolveRoot()
        return when (name) {
            "read" -> fileTools.read(
                root = root,
                path = requireString(args, "path"),
                startLine = optionalInt(args, "start_line"),
                endLine = optionalInt(args, "end_line"),
            )
            "list" -> fileTools.list(root, args.optString("path", ""))
            "search" -> fileTools.search(
                root = root,
                query = requireString(args, "query"),
                path = args.optString("path", ""),
            )
            "write" -> fileTools.write(
                root = root,
                path = requireString(args, "path"),
                content = requireString(args, "content"),
            )
            "edit" -> fileTools.edit(
                root = root,
                path = requireString(args, "path"),
                oldStr = requireString(args, "old_str"),
                newStr = requireString(args, "new_str"),
            )
            else -> "Tool tidak dikenal: \"$name\"."
        }
    }

    private suspend fun resolveRoot(): DocumentFile {
        val uri = workspace.workspaceUri.first()
            ?: throw WorkspacePathException("Folder kerja belum dipilih. Minta user memilih folder dulu.")
        return DocumentFile.fromTreeUri(context, uri)
            ?: throw WorkspacePathException("Folder kerja tidak bisa diakses.")
    }

    private fun requireString(args: JSONObject, key: String): String {
        if (!args.has(key)) throw WorkspacePathException("Parameter \"$key\" wajib.")
        return args.optString(key, "")
    }

    private fun optionalInt(args: JSONObject, key: String): Int? =
        if (args.has(key) && !args.isNull(key)) args.optInt(key, 0) else null

    fun specs(): List<ToolSpec> = listOf(
        ToolSpec(
            name = "read",
            description = "Baca isi file teks di folder workspace. Path relatif terhadap folder kerja. Opsional: rentang baris (1-based).",
            parameters = schema(
                required = arrayOf("path"),
                properties = JSONObject()
                    .putProp("path", "string", "Path file relatif terhadap folder kerja")
                    .putProp("start_line", "integer", "Baris awal, 1-based (opsional)")
                    .putProp("end_line", "integer", "Baris akhir, inklusif (opsional)"),
            ),
        ),
        ToolSpec(
            name = "list",
            description = "Daftar isi folder (subfolder dan file). Path kosong atau '.' untuk root workspace.",
            parameters = schema(
                required = emptyArray(),
                properties = JSONObject()
                    .putProp("path", "string", "Path folder relatif; kosong untuk root"),
            ),
        ),
        ToolSpec(
            name = "search",
            description = "Cari teks (case-sensitive) di file dalam folder workspace. Hasil berformat path:baris:teks.",
            parameters = schema(
                required = arrayOf("query"),
                properties = JSONObject()
                    .putProp("query", "string", "Teks yang dicari")
                    .putProp("path", "string", "Subfolder untuk membatasi pencarian (opsional)"),
            ),
        ),
        ToolSpec(
            name = "write",
            description = "Buat file baru atau timpa isi file yang ada dengan konten penuh. Folder induk dibuat otomatis.",
            parameters = schema(
                required = arrayOf("path", "content"),
                properties = JSONObject()
                    .putProp("path", "string", "Path file relatif terhadap folder kerja")
                    .putProp("content", "string", "Isi file penuh"),
            ),
        ),
        ToolSpec(
            name = "edit",
            description = "Ganti old_str dengan new_str di satu file. old_str harus muncul tepat satu kali; kalau tidak, error.",
            parameters = schema(
                required = arrayOf("path", "old_str", "new_str"),
                properties = JSONObject()
                    .putProp("path", "string", "Path file relatif terhadap folder kerja")
                    .putProp("old_str", "string", "Teks lama yang harus unik di file")
                    .putProp("new_str", "string", "Teks pengganti; kosong untuk menghapus"),
            ),
        ),
        ToolSpec(
            name = "question",
            description = "Tanya user langsung di aplikasi. Pakai hanya jika benar-benar perlu; " +
                "hal yang bisa dicari sendiri lewat read/list/search jangan ditanya. " +
                "Maksimal 3 pertanyaan per panggilan, tiap pertanyaan 2–4 option.",
            parameters = JSONObject()
                .put("type", "object")
                .put("required", JSONArray("questions"))
                .put(
                    "properties",
                    JSONObject().put(
                        "questions",
                        JSONObject()
                            .put("type", "array")
                            .put("maxItems", 3)
                            .put(
                                "items",
                                JSONObject()
                                    .put("type", "object")
                                    .put("required", JSONArray(arrayOf("question", "options")))
                                    .put(
                                        "properties",
                                        JSONObject()
                                            .put("question", JSONObject().put("type", "string"))
                                            .put(
                                                "options",
                                                JSONObject()
                                                    .put("type", "array")
                                                    .put("minItems", 2)
                                                    .put("maxItems", 4)
                                                    .put(
                                                        "items",
                                                        JSONObject().put("type", "string"),
                                                    ),
                                            )
                                            .put("multiSelect", JSONObject().put("type", "boolean"))
                                            .put("allowCustom", JSONObject().put("type", "boolean")),
                                    ),
                            ),
                    ),
                ),
        ),
    )

    private fun schema(required: Array<String>, properties: JSONObject): JSONObject =
        JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray(required))
            .put("additionalProperties", false)

    private fun JSONObject.putProp(name: String, type: String, description: String): JSONObject =
        put(name, JSONObject().put("type", type).put("description", description))
}

sealed interface PreviewResult {
    data class Ready(val path: String, val isNewFile: Boolean, val diff: DiffPreview) : PreviewResult
    data class Failed(val message: String) : PreviewResult
}
