package com.example.aiagent.tools

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WebSearchTool : AgentTool {

    override val name = "web_search"
    override val description = "Search the internet for information and return relevant snippets"
    override val parameters = listOf(
        ToolParameter("query", "string", "The search query or question")
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun execute(context: Context, params: Map<String, String>): String {
        val query = params["query"] ?: return "Error: Required parameter 'query' is missing"

        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://www.google.com/search?q=$encodedQuery&hl=en"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withContext "❌ Search failed with HTTP ${response.code}"
                }

                // Strip HTML and extract readable text
                val cleanText = body
                    .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
                    .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("&[a-zA-Z]+;"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                if (cleanText.isBlank()) {
                    return@withContext "⚠️ No search results found for '$query'"
                }

                // Truncate to a reasonable size
                val truncated = if (cleanText.length > 3000) {
                    cleanText.substring(0, 3000) + "\n\n[... truncated ...]"
                } else {
                    cleanText
                }

                "🔍 Search results for '$query':\n$truncated"
            } catch (e: Exception) {
                "❌ Search error: ${e.message}"
            }
        }
    }
}
