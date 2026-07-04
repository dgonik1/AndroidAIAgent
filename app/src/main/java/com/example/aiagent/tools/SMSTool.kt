package com.example.aiagent.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

class SMSTool : AgentTool {

    override val name = "send_sms"
    override val description = "Send an SMS text message to a phone number"
    override val parameters = listOf(
        ToolParameter("phone_number", "string", "Recipient phone number (e.g., +71234567890)"),
        ToolParameter("message", "string", "Text content of the message to send")
    )

    override suspend fun execute(context: Context, params: Map<String, String>): String {
        val phoneNumber = params["phone_number"] ?: return "Error: Required parameter 'phone_number' is missing"
        val message = params["message"] ?: return "Error: Required parameter 'message' is missing"

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "Error: SEND_SMS permission not granted. Please grant SMS permission in app settings."
        }

        return try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            "✅ SMS successfully sent to $phoneNumber"
        } catch (e: Exception) {
            "❌ Failed to send SMS: ${e.message}"
        }
    }
}
