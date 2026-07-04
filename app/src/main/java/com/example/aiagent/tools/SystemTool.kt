package com.example.aiagent.tools

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings

class SystemTool : AgentTool {

    override val name = "system_control"
    override val description = "Control device settings: WiFi, Bluetooth, screen brightness"
    override val parameters = listOf(
        ToolParameter("action", "string", "One of: wifi_on, wifi_off, bluetooth_on, " +
                "bluetooth_off, set_brightness, get_brightness, get_info"),
        ToolParameter("value", "string", "Value for the action " +
                "(e.g., brightness level 0-255, not always required)")
    )

    override suspend fun execute(context: Context, params: Map<String, String>): String {
        val action = params["action"] ?: return "Error: Required parameter 'action' is missing"

        return try {
            when (action) {
                "wifi_on" -> turnWifi(context, true)
                "wifi_off" -> turnWifi(context, false)
                "bluetooth_on" -> turnBluetooth(true)
                "bluetooth_off" -> turnBluetooth(false)
                "set_brightness" -> setBrightness(context, params["value"])
                "get_brightness" -> getBrightness(context)
                "get_info" -> getDeviceInfo()
                else -> "Error: Unknown action '$action'"
            }
        } catch (e: SecurityException) {
            "❌ Permission denied: ${e.message}. Please grant the required permission in app settings."
        } catch (e: Exception) {
            "❌ System control error: ${e.message}"
        }
    }

    private fun turnWifi(context: Context, enable: Boolean): String {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return "⚠️ WiFi is not available on this device"

        return if (wifiManager.isWifiEnabled == enable) {
            "WiFi is already ${if (enable) "on" else "off"}"
        } else {
            wifiManager.isWifiEnabled = enable
            "✅ WiFi turned ${if (enable) "on" else "off"}"
        }
    }

    private fun turnBluetooth(enable: Boolean): String {
        val btAdapter = BluetoothAdapter.getDefaultAdapter()
            ?: return "⚠️ Bluetooth is not available on this device"

        return if (btAdapter.isEnabled == enable) {
            "Bluetooth is already ${if (enable) "on" else "off"}"
        } else {
            val success = if (enable) btAdapter.enable() else btAdapter.disable()
            if (success) "✅ Bluetooth turned ${if (enable) "on" else "off"}"
            else "❌ Failed to ${if (enable) "enable" else "disable"} Bluetooth"
        }
    }

    private fun setBrightness(context: Context, valueStr: String?): String {
        val brightness = valueStr?.toIntOrNull()
            ?: return "Error: 'value' parameter must be a number between 0 and 255"

        if (brightness !in 0..255) {
            return "Error: Brightness must be between 0 and 255"
        }

        val resolver = context.applicationContext.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(context)) {
                return "⚠️ Cannot change brightness: WRITE_SETTINGS permission not granted. " +
                        "Please allow it in app settings."
            }
        }

        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
        return "✅ Screen brightness set to $brightness / 255"
    }

    private fun getBrightness(context: Context): String {
        val resolver = context.applicationContext.contentResolver
        val brightness = Settings.System.getInt(
            resolver, Settings.System.SCREEN_BRIGHTNESS, 128
        )
        val mode = Settings.System.getInt(
            resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        val modeStr = if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            "automatic"
        } else {
            "manual"
        }
        return "📱 Current brightness: $brightness / 255 (mode: $modeStr)"
    }

    private fun getDeviceInfo(): String {
        val info = buildString {
            appendLine("📱 Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("🤖 Android version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("🏷️ Brand: ${Build.BRAND}")
            append("🌐 Board: ${Build.BOARD}")
        }
        return info
    }
}
