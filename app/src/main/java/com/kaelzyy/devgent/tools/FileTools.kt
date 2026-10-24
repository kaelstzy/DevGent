package com.kaelzyy.devgent.tools

import android.content.ContentResolver
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kaelzyy.devgent.workspace.WorkspacePath
import com.kaelzyy.devgent.workspace.WorkspacePathException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale

private const val READ_MAX_BYTES = 200 * 1024
private const val EDIT_MAX_BYTES = 512 * 1024
private const val SEARCH_MAX_FILE_BYTES = 512 * 1024
private const val SEARCH_MAX_MATCHES = 100
private const val SEARCH_MAX_FILES = 2000
private const val SEARCH_MAX_DEPTH = 12
private const val SEARCH_LINE_MAX_CHARS = 240
private val SKIP_DIRS = setOf(".git", "node_modules", ".gradle", "build", ".idea")

class FileTools(private val resolver: ContentResolver) {

    suspend fun read(
        root: DocumentFile,
        path: String,
        startLine: Int? = null,
        endLine: Int? = null,
    ): String = withContext(Dispatchers.IO) {
        toolResult {
            if (startLine != null && startLine < 1) throw WorkspacePathException("start_line minimal 1.")
            if (endLine != null && endLine < 1) throw WorkspacePathException("end_line minimal 1.")
            if (startLine != null && endLine != null && endLine < startLine) {
                throw WorkspacePathException("end_line lebih kecil dari start_line.")
            }
            val file = WorkspacePath.resolve(root, path)
            if (!file.isFile) throw WorkspacePathException("Bukan file: \"$path\".")
            val read = readText(file.uri, READ_MAX_BYTES)
            if (read.text.contains('\u0000')) throw WorkspacePathException("Bukan file teks: \"$path\".")

            if (startLine == null && endLine == null) {
                val body = read.text.ifEmpty { "(file kosong)" }
                body + noteIf(read.truncated) {
                    "Catatan: file lebih besar dari $READ_MAX_BYTES byte dan konten dipotong."
                }
            } else {
                val lines = read.text.split('\n')
                val from = startLine ?: 1
                if (from > lines.size) {
                    throw WorkspacePathException("start_line melewati akhir file (${lines.size} baris).")
                }
                val to = minOf(endLine ?: lines.size, lines.size)
                val slice = lines.subList(from - 1, to).joinToString("\n")
                val notes = buildList {
                    add("Catatan: menampilkan baris $from-$to dari ${lines.size}.")
                    if (read.truncated) {
                        add("Catatan: file lebih besar dari $READ_MAX_BYTES byte dan konten dipotong.")
                    }
                }
                slice + "\n\n" + notes.joinToString("\n")
            }
        }
    }

    suspend fun list(root: DocumentFile, path: String = ""): String =
        withContext(Dispatchers.IO) {
            toolResult {
                val folder = WorkspacePath.resolveAllowRoot(root, path)
                if (!folder.isDirectory) throw WorkspacePathException("Bukan folder: \"$path\".")
                val children = folder.listFiles()
                    .mapNotNull { child -> child.name?.let { name -> name to child } }
                    .sortedWith(compareBy({ !it.second.isDirectory }, { it.first.lowercase() }))
                if (children.isEmpty()) {
                    "Folder kosong."
                } else {
                    children.joinToString("\n") { (name, child) ->
                        if (child.isDirectory) "[DIR]  $name/" else "[FILE] $name${child.length().toSizeLabel()}"
                    }
                }
            }
        }

    suspend fun search(root: DocumentFile, query: String, path: String = ""): String =
        withContext(Dispatchers.IO) {
            toolResult {
                if (query.isEmpty()) throw WorkspacePathException("Query kosong.")
                val start = WorkspacePath.resolveAllowRoot(root, path)
                if (!start.isDirectory) throw WorkspacePathException("Bukan folder: \"$path\".")
                val segments = WorkspacePath.normalize(path, allowRoot = true)
                val prefix = if (segments.isEmpty()) "" else segments.joinToString("/") + "/"

                val results = mutableListOf<String>()
                var matchCount = 0
                var scanned = 0
                var limitHit = false

                fun walk(dir: DocumentFile, rel: String, depth: Int) {
                    if (limitHit || depth > SEARCH_MAX_DEPTH) return
                    for (child in dir.listFiles()) {
                        if (limitHit) return
                        val name = child.name ?: continue
                        if (child.isDirectory) {
                            if (name in SKIP_DIRS) continue
                            walk(child, rel + name + "/", depth + 1)
                        } else {
                            if (scanned >= SEARCH_MAX_FILES) {
                                limitHit = true
                                return
                            }
                            scanned++
                            val read = readText(child.uri, SEARCH_MAX_FILE_BYTES)
                            if (read.text.contains('\u0000')) continue
                            val lines = read.text.split('\n')
                            for ((index, line) in lines.withIndex()) {
                                if (!line.contains(query)) continue
                                matchCount++
                                val clean = line.removeSuffix("\r").let {
                                    if (it.length > SEARCH_LINE_MAX_CHARS) it.take(SEARCH_LINE_MAX_CHARS) + "…" else it
                                }
                                results += "$rel$name:${index + 1}:$clean"
                                if (matchCount >= SEARCH_MAX_MATCHES) {
                                    limitHit = true
                                    return
                                }
                            }
                        }
                    }
                }

                walk(start, prefix, 0)
                if (results.isEmpty()) {
                    "Tidak ada hasil untuk \"$query\"."
                } else {
                    buildString {
                        append(results.joinToString("\n"))
                        if (limitHit) {
                            append("\n… hasil dipotong (maks $SEARCH_MAX_MATCHES kecocokan atau batas file tercapai).")
                        }
                    }
                }
            }
        }

    suspend fun write(root: DocumentFile, path: String, content: String): String =
        withContext(Dispatchers.IO) {
            toolResult {
                val target = WorkspacePath.resolveParent(root, path, createMissing = true)
                val existing = target.parent.findFile(target.name)
                if (existing?.isDirectory == true) throw WorkspacePathException("Path adalah folder: \"$path\".")
                val file = existing
                    ?: target.parent.createFile(mimeTypeOf(target.name), target.name)
                    ?: throw WorkspacePathException("Tidak bisa membuat file: \"$path\".")
                writeText(file.uri, content)
                val verb = if (existing == null) "File dibuat" else "File ditimpa"
                "$verb: $path (${content.toByteArray(Charsets.UTF_8).size} byte)"
            }
        }

    suspend fun previewWrite(root: DocumentFile, path: String, content: String): DiffPreview =
        withContext(Dispatchers.IO) {
            val target = WorkspacePath.resolveParent(root, path, createMissing = false)
            val existing = target.parent.findFile(target.name)
            if (existing?.isDirectory == true) throw WorkspacePathException("Path adalah folder: \"$path\".")
            val old = if (existing != null) readText(existing.uri, EDIT_MAX_BYTES).text else ""
            Diff.compute(old, content)
        }

    suspend fun previewEdit(
        root: DocumentFile,
        path: String,
        oldStr: String,
        newStr: String,
    ): DiffPreview = withContext(Dispatchers.IO) {
        if (oldStr.isEmpty()) throw WorkspacePathException("old_str kosong.")
        if (oldStr == newStr) throw WorkspacePathException("old_str dan new_str sama.")
        val file = WorkspacePath.resolve(root, path)
        if (!file.isFile) throw WorkspacePathException("Bukan file: \"$path\".")
        val read = readText(file.uri, EDIT_MAX_BYTES)
        if (read.truncated) {
            throw WorkspacePathException("File terlalu besar untuk edit (maks $EDIT_MAX_BYTES byte).")
        }
        val text = read.text
        requireUniqueOldStr(text, oldStr, path)
        Diff.compute(text, text.replace(oldStr, newStr))
    }

    suspend fun readRawOrNull(root: DocumentFile, path: String): String? =
        withContext(Dispatchers.IO) {
            val file = runCatching { WorkspacePath.resolve(root, path) }.getOrNull()
                ?: return@withContext null
            if (!file.isFile) return@withContext null
            readText(file.uri, EDIT_MAX_BYTES).text
        }

    suspend fun restore(root: DocumentFile, path: String, before: String?): String =
        withContext(Dispatchers.IO) {
            toolResult {
                val target = WorkspacePath.resolveParent(root, path, createMissing = false)
                val existing = target.parent.findFile(target.name)
                    ?: throw WorkspacePathException("File tidak ditemukan: \"$path\".")
                if (before == null) {
                    if (!existing.delete()) throw WorkspacePathException("Tidak bisa menghapus file: \"$path\".")
                    "File baru dihapus: $path"
                } else {
                    writeText(existing.uri, before)
                    "Isi \"$path\" dikembalikan."
                }
            }
        }

    suspend fun edit(
        root: DocumentFile,
        path: String,
        oldStr: String,
        newStr: String,
    ): String = withContext(Dispatchers.IO) {
        toolResult {
            if (oldStr.isEmpty()) throw WorkspacePathException("old_str kosong.")
            if (oldStr == newStr) throw WorkspacePathException("old_str dan new_str sama.")
            val file = WorkspacePath.resolve(root, path)
            if (!file.isFile) throw WorkspacePathException("Bukan file: \"$path\".")
            val read = readText(file.uri, EDIT_MAX_BYTES)
            if (read.truncated) {
                throw WorkspacePathException("File terlalu besar untuk edit (maks $EDIT_MAX_BYTES byte).")
            }
            val text = read.text
            requireUniqueOldStr(text, oldStr, path)
            val updated = text.replace(oldStr, newStr)
            writeText(file.uri, updated)
            "OK: \"$path\" diubah (1 kecocokan, ${text.length} -> ${updated.length} char)."
        }
    }

    private fun requireUniqueOldStr(text: String, oldStr: String, path: String) {
        var count = 0
        var index = text.indexOf(oldStr)
        while (index >= 0) {
            count++
            index = text.indexOf(oldStr, index + 1)
        }
        when (count) {
            0 -> throw WorkspacePathException("old_str tidak ditemukan di \"$path\".")
            1 -> Unit
            else -> throw WorkspacePathException("old_str tidak unik ($count kecocokan) di \"$path\".")
        }
    }

    private data class TextRead(val text: String, val truncated: Boolean)

    private fun readText(uri: Uri, maxBytes: Int): TextRead {
        val input = resolver.openInputStream(uri)
            ?: throw WorkspacePathException("Tidak bisa membuka file.")
        input.use { stream ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8 * 1024)
            var total = 0
            var truncated = false
            while (true) {
                val n = stream.read(chunk)
                if (n < 0) break
                if (total + n > maxBytes) {
                    buffer.write(chunk, 0, maxBytes - total)
                    truncated = true
                    break
                }
                buffer.write(chunk, 0, n)
                total += n
            }
            var text = String(buffer.toByteArray(), Charsets.UTF_8)
            if (truncated) {
                val cut = text.lastIndexOf('\n')
                if (cut >= 0) text = text.substring(0, cut + 1)
            }
            return TextRead(text, truncated)
        }
    }

    private fun writeText(uri: Uri, content: String) {
        val output = resolver.openOutputStream(uri, "w")
            ?: throw WorkspacePathException("Tidak bisa menulis file.")
        output.use { it.write(content.toByteArray(Charsets.UTF_8)) }
    }

    private inline fun toolResult(block: () -> String): String =
        runCatching(block).getOrElse { e ->
            if (e is CancellationException) throw e
            e.userMessage()
        }

    private fun mimeTypeOf(name: String): String =
        when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "html", "htm" -> "text/html"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            else -> "text/plain"
        }

    private fun noteIf(condition: Boolean, note: () -> String): String =
        if (condition) "\n\n${note()}" else ""
}

internal fun Throwable.userMessage(): String = when (this) {
    is WorkspacePathException -> message ?: "Path tidak valid."
    is SecurityException -> "Akses ditolak."
    is IOException -> "Gagal mengakses file: ${message ?: "kesalahan I/O"}."
    else -> "Gagal: ${message ?: "kesalahan tidak diketahui"}"
}

private fun Long.toSizeLabel(): String = when {
    this < 0 -> ""
    this < 1024L * 1024 -> String.format(Locale.ROOT, " (%.1f KB)", this / 1024.0)
    else -> String.format(Locale.ROOT, " (%.1f MB)", this / (1024.0 * 1024.0))
}
