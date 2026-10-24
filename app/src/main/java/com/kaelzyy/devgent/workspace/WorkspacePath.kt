package com.kaelzyy.devgent.workspace

import androidx.documentfile.provider.DocumentFile

class WorkspacePathException(message: String) : Exception(message)

data class ParentTarget(val parent: DocumentFile, val name: String)

object WorkspacePath {

    fun normalize(path: String, allowRoot: Boolean = false): List<String> {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) {
            if (allowRoot) return emptyList()
            throw WorkspacePathException("Path kosong.")
        }
        if (trimmed.startsWith("/")) {
            throw WorkspacePathException("Path absolut tidak diizinkan: \"$path\".")
        }
        val segments = mutableListOf<String>()
        for (segment in trimmed.split("/")) {
            when {
                segment.isEmpty() || segment == "." -> continue
                segment == ".." -> throw WorkspacePathException("Segmen \"..\" tidak diizinkan.")
                else -> segments.add(segment)
            }
        }
        if (segments.isEmpty()) {
            if (allowRoot) return emptyList()
            throw WorkspacePathException("Path kosong.")
        }
        return segments
    }

    fun resolve(root: DocumentFile, path: String): DocumentFile {
        requireRoot(root)
        return walk(root, normalize(path))
    }

    fun resolveAllowRoot(root: DocumentFile, path: String): DocumentFile {
        requireRoot(root)
        val segments = normalize(path, allowRoot = true)
        return if (segments.isEmpty()) root else walk(root, segments)
    }

    fun resolveParent(root: DocumentFile, path: String, createMissing: Boolean): ParentTarget {
        requireRoot(root)
        val segments = normalize(path)
        val name = segments.last()
        var current = root
        for (segment in segments.dropLast(1)) {
            val child = current.findFile(segment)
            when {
                child == null && createMissing -> current = current.createDirectory(segment)
                    ?: throw WorkspacePathException("Tidak bisa membuat folder: \"$segment\".")
                child == null -> throw WorkspacePathException("Folder tidak ditemukan: \"$segment\".")
                !child.isDirectory -> throw WorkspacePathException("Bukan folder: \"$segment\".")
                else -> current = child
            }
        }
        return ParentTarget(current, name)
    }

    private fun requireRoot(root: DocumentFile) {
        if (!root.isDirectory) throw WorkspacePathException("Folder root bukan direktori.")
    }

    private fun walk(root: DocumentFile, segments: List<String>): DocumentFile {
        var current = root
        for (segment in segments) {
            val child = current.findFile(segment)
                ?: throw WorkspacePathException("File tidak ditemukan: \"$segment\".")
            current = child
        }
        return current
    }
}
