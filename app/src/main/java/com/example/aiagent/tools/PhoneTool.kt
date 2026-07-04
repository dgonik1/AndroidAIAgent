package com.example.aiagent.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat

class PhoneTool : AgentTool {

    override val name = "make_call"
    override val description = "Initiate a phone call to a specified number"
    override val parameters = listOf(
        ToolParameter("phone_number", "string", "Phone number to call (e.g., +71234567890)")
    )

    override suspend fun execute(context: Context, params: Map<String, String>): String {
        val phoneNumber = params["phone_number"] ?: return "Error: Required parameter 'phone_number' is missing"

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "Error: CALL_PHONE permission not granted. Please grant Phone permission in app settings."
        }

        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "📞 Call initiated to $phoneNumber"
        } catch (e: Exception) {
            "❌ Failed to make call: ${e.message}"
        }
    }
}
