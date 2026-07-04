package com.example.aiagent.ui

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val role: MessageRole,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageRole {
    USER,
    AGENT,
    SYSTEM
}

sealed class AgentEvent {
    data class Thinking(val thought: String) : AgentEvent()
    data class ToolCall(val tool: String, val params: Map<String, String>) : AgentEvent()
    data class ToolResult(val tool: String, val result: String) : AgentEvent()
    data class Response(val content: String) : AgentEvent()
    data class Error(val message: String) : AgentEvent()
}
