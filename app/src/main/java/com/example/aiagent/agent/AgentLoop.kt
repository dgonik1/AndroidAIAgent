package com.example.aiagent.agent

import android.content.Context
import com.example.aiagent.tools.AgentTool
import com.example.aiagent.ui.AgentEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject

class AgentLoop(
    private val llmEngine: LLMEngine,
    private val tools: List<AgentTool>,
    private val context: Context
) {

    private val toolMap: Map<String, AgentTool> = tools.associateBy { it.name }

    fun processInput(userInput: String): Flow<AgentEvent> = flow {
        val systemPrompt = buildSystemPrompt()
        val messages = mutableListOf(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf("role" to "user", "content" to userInput)
        )

        emit(AgentEvent.Thinking("Analyzing your request..."))

        for (iteration in 0 until 10) {
            emit(AgentEvent.Thinking("Step ${iteration + 1}: processing..."))

            val prompt = formatConversation(messages)
            val response = llmEngine.generate(prompt)

            if (response == null) {
                val err = llmEngine.initializationError
                if (err != null) {
                    emit(AgentEvent.Error(err))
                } else {
                    emit(AgentEvent.Error("LLM failed to generate a response"))
                }
                return@flow
            }

            val parsed = parseLLMResponse(response)

            when (parsed) {
                is ParsedAction -> {
                    emit(AgentEvent.ToolCall(parsed.toolName, parsed.params))
                    emit(AgentEvent.Thinking("Executing ${parsed.toolName}..."))

                    val result = executeTool(parsed.toolName, parsed.params)

                    emit(AgentEvent.ToolResult(parsed.toolName, result))

                    messages.add(mapOf("role" to "assistant", "content" to response))
                    messages.add(mapOf(
                        "role" to "system",
                        "content" to "Result of ${parsed.toolName}(${parsed.params}):\n$result"
                    ))
                }
                is ParsedFinalResponse -> {
                    emit(AgentEvent.Response(parsed.content))
                    return@flow
                }
                is ParsedError -> {
                    emit(AgentEvent.Response(response))
                    return@flow
                }
            }
        }

        emit(AgentEvent.Response(
            "I've reached the maximum number of steps (10). " +
                    "Please provide more specific instructions or ask a simpler question."
        ))
    }

    private fun buildSystemPrompt(): String {
        val toolDescriptions = tools.joinToString("\n") { tool ->
            val paramsDesc = if (tool.parameters.isEmpty()) {
                "No parameters"
            } else {
                tool.parameters.joinToString(", ") { param ->
                    "${param.name}: ${param.type}${if (!param.required) " (optional)" else ""}" +
                            " - ${param.description}"
                }
            }
            "  \u2022 ${tool.name}: ${tool.description}\n    Parameters: {$paramsDesc}"
        }

        return """You are an AI assistant running on an Android phone. Your purpose is to help the user by using the tools available to you.

AVAILABLE TOOLS:
$toolDescriptions

INSTRUCTIONS:
- You must ALWAYS respond in valid JSON format.
- To use a tool, respond with:
  {"action": "tool_name", "params": {"param1": "value1", "param2": "value2"}}
- To respond to the user, respond with:
  {"action": "respond", "content": "Your message to the user here"}

RULES:
1. Only call ONE tool at a time.
2. Provide ALL required parameters for the tool.
3. Analyze the tool result carefully before deciding the next step.
4. When you have enough information, respond to the user with a clear answer.
5. Be concise, helpful, and accurate.
6. If a tool returns an error, try an alternative approach or apologize to the user.
7. Do NOT make up information - use tools to get real data.
8. For web_search, provide a summary of the search results.

Remember: respond ONLY with JSON, no other text."""
    }

    private fun formatConversation(messages: List<Map<String, String>>): String {
        val sb = StringBuilder()
        for (msg in messages) {
            val role = msg["role"] ?: "user"
            val content = msg["content"] ?: ""
            when (role) {
                "system" -> sb.append("<start_of_turn>system\n$content\n<end_of_turn>\n")
                "user" -> sb.append("<start_of_turn>user\n$content\n<end_of_turn>\n")
                "assistant" -> sb.append("<start_of_turn>model\n$content\n<end_of_turn>\n")
            }
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun parseLLMResponse(response: String): ParsedResponse {
        val jsonStr = extractJson(response)
        if (jsonStr != null) {
            try {
                val json = JSONObject(jsonStr)
                val action = json.optString("action", "")

                return when (action) {
                    "respond" -> {
                        val content = json.optString("content", "")
                        if (content.isBlank()) {
                            ParsedFinalResponse(response)
                        } else {
                            ParsedFinalResponse(content)
                        }
                    }
                    else -> {
                        if (action.isNotBlank()) {
                            val paramsJson = json.optJSONObject("params")
                            val params = mutableMapOf<String, String>()
                            if (paramsJson != null) {
                                for (key in paramsJson.keys()) {
                                    params[key] = paramsJson.optString(key, "")
                                }
                            }
                            ParsedAction(action, params)
                        } else {
                            ParsedError()
                        }
                    }
                }
            } catch (e: Exception) {
                ParsedError()
            }
        } else {
            ParsedError()
        }
    }

    private fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1)
        }
        return null
    }

    private suspend fun executeTool(toolName: String, params: Map<String, String>): String {
        val tool = toolMap[toolName]
        if (tool == null) {
            return "Error: Unknown tool '$toolName'. Available tools: ${toolMap.keys.joinToString(", ")}"
        }
        return try {
            tool.execute(context, params)
        } catch (e: Exception) {
            "Error executing $toolName: ${e.message}"
        }
    }
}

private sealed class ParsedResponse
private data class ParsedAction(
    val toolName: String,
    val params: Map<String, String>
) : ParsedResponse()
private data class ParsedFinalResponse(val content: String) : ParsedResponse()
private class ParsedError : ParsedResponse()
