package com.example.aiagent.tools

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileTool : AgentTool {

    override val name = "file_operation"
    override val description = "Read, write, or list files in the app's storage directory"
    override val parameters = listOf(
        ToolParameter("operation", "string", "One of: read, write, list, delete"),
        ToolParameter("path", "string", "Relative file path (e.g., notes/hello.txt)"),
        ToolParameter("content", "string", "Content to write (required for write operation)")
    )

    override suspend fun execute(context: Context, params: Map<String, String>): String {
        val operation = params["operation"]?.lowercase() ?: return "Error: Required parameter 'operation' is missing"
        val path = params["path"] ?: return "Error: Required parameter 'path' is missing"

        return withContext(Dispatchers.IO) {
            try {
                val baseDir = File(context.filesDir, "agent_files")
                baseDir.mkdirs()
                val file = File(baseDir, path)

                when (operation) {
                    "read" -> {
                        if (!file.exists()) return@withContext "⚠️ File not found: ${file.absolutePath}"
                        val content = file.readText()
                        "📄 Content of ${file.name}:\n$content"
                    }
                    "write" -> {
                        val content = params["content"] ?: return@withContext "Error: 'content' parameter required for write operation"
                        file.parentFile?.mkdirs()
                        file.writeText(content)
                        "✅ File written to ${file.absolutePath} (${content.length} chars)"
                    }
                    "list" -> {
                        val parent = if (file.isDirectory) file else file.parentFile
                        if (parent == null || !parent.exists()) return@withContext "⚠️ Directory not found"
                        val files = parent.listFiles()
                        if (files.isNullOrEmpty()) return@withContext "📂 Directory is empty: ${parent.absolutePath}"
                        val listing = files.joinToString("\n") { f ->
                            val type = if (f.isDirectory) "📁" else "📄"
                            "$type ${f.name} (${formatSize(f.length())})"
                        }
                        "📂 Files in ${parent.absolutePath}:\n$listing"
                    }
                    "delete" -> {
                        if (!file.exists()) return@withContext "⚠️ File not found: ${file.absolutePath}"
                        if (file.delete()) "✅ Deleted: ${file.absolutePath}"
                        else "❌ Failed to delete: ${file.absolutePath}"
                    }
                    else -> "Error: Unknown operation '$operation'. Use: read, write, list, delete"
                }
            } catch (e: Exception) {
                "❌ File operation error: ${e.message}"
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }
}
