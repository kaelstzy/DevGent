package com.kaelzyy.devgent.tools

enum class DiffLineType { CONTEXT, ADD, REMOVE }

data class DiffLine(val type: DiffLineType, val text: String)

data class DiffPreview(
    val added: Int,
    val removed: Int,
    val lines: List<DiffLine>,
    val truncatedCount: Int,
)

object Diff {

    const val MAX_LINES = 200
    private const val MAX_LCS_CELLS = 250_000L

    fun compute(old: String, new: String): DiffPreview {
        val oldLines = splitLines(old)
        val newLines = splitLines(new)
        var prefix = 0
        val minSize = minOf(oldLines.size, newLines.size)
        while (prefix < minSize && oldLines[prefix] == newLines[prefix]) prefix++
        var suffix = 0
        while (suffix < minSize - prefix &&
            oldLines[oldLines.size - 1 - suffix] == newLines[newLines.size - 1 - suffix]
        ) {
            suffix++
        }
        val midOld = oldLines.subList(prefix, oldLines.size - suffix)
        val midNew = newLines.subList(prefix, newLines.size - suffix)
        val mid = if (midOld.size.toLong() * midNew.size > MAX_LCS_CELLS) {
            midOld.map { DiffLine(DiffLineType.REMOVE, it) } +
                midNew.map { DiffLine(DiffLineType.ADD, it) }
        } else {
            lcsDiff(midOld, midNew)
        }
        val added = mid.count { it.type == DiffLineType.ADD }
        val removed = mid.count { it.type == DiffLineType.REMOVE }
        val lines = if (mid.size > MAX_LINES) mid.subList(0, MAX_LINES) else mid
        return DiffPreview(
            added = added,
            removed = removed,
            lines = lines,
            truncatedCount = mid.size - lines.size,
        )
    }

    private fun splitLines(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val parts = text.split('\n')
        return if (parts.last().isEmpty()) parts.dropLast(1) else parts
    }

    private fun lcsDiff(a: List<String>, b: List<String>): List<DiffLine> {
        val m = a.size
        val n = b.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in m - 1 downTo 0) {
            for (j in n - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j]) {
                    dp[i + 1][j + 1] + 1
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }
        val out = ArrayList<DiffLine>(m + n)
        var i = 0
        var j = 0
        while (i < m && j < n) {
            when {
                a[i] == b[j] -> {
                    out.add(DiffLine(DiffLineType.CONTEXT, a[i]))
                    i++
                    j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> {
                    out.add(DiffLine(DiffLineType.REMOVE, a[i]))
                    i++
                }
                else -> {
                    out.add(DiffLine(DiffLineType.ADD, b[j]))
                    j++
                }
            }
        }
        while (i < m) {
            out.add(DiffLine(DiffLineType.REMOVE, a[i]))
            i++
        }
        while (j < n) {
            out.add(DiffLine(DiffLineType.ADD, b[j]))
            j++
        }
        return out
    }
}
