package com.example.aiagent.tools

import android.content.Context
import kotlinx.coroutines.CompletableDeferred

class CameraTool(
    private val capturePhoto: suspend () -> String
) : AgentTool {

    override val name = "take_photo"
    override val description = "Capture a photo using the device camera and return the image file path"
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(context: Context, params: Map<String, String>): String {
        return try {
            val photoPath = capturePhoto()
            "📸 Photo captured successfully. Saved at: $photoPath"
        } catch (e: Exception) {
            "❌ Failed to capture photo: ${e.message}"
        }
    }
}
