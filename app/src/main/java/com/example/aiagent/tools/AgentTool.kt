package com.example.aiagent.tools

import android.content.Context

interface AgentTool {
    val name: String
    val description: String
    val parameters: List<ToolParameter>

    suspend fun execute(context: Context, params: Map<String, String>): String
}

data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true
)
