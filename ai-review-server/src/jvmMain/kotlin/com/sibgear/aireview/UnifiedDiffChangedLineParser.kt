package com.sibgear.aireview

object UnifiedDiffChangedLineParser {
    fun parse(diff: String): Map<String, Set<Int>> {
        val changedLinesByPath = mutableMapOf<String, MutableSet<Int>>()
        var currentPath: String? = null
        var newLine: Int? = null

        diff.lineSequence().forEach { line ->
            when {
                line.startsWith("+++ ") -> {
                    currentPath = line.removePrefix("+++ ")
                        .removePrefix("b/")
                        .takeUnless { it == "/dev/null" }
                    newLine = null
                }
                line.startsWith("@@") -> {
                    newLine = parseNewStartLine(line)
                }
                line.startsWith("+") && !line.startsWith("+++") -> {
                    val path = currentPath
                    val lineNumber = newLine
                    if (path != null && lineNumber != null) {
                        changedLinesByPath.getOrPut(path) { mutableSetOf() } += lineNumber
                        newLine = lineNumber + 1
                    }
                }
                line.startsWith("-") && !line.startsWith("---") -> Unit
                newLine != null -> {
                    newLine = newLine + 1
                }
            }
        }

        return changedLinesByPath
    }

    private fun parseNewStartLine(hunkHeader: String): Int? {
        val match = Regex("@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@").find(hunkHeader)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
