package com.example.aiagent.agent

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LLMEngine(private val context: Context) {

    private var inference: LlmInference? = null
    private var initialized = false
    private var initError: String? = null

    val isInitialized: Boolean get() = initialized
    val initializationError: String? get() = initError

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext

        try {
            val modelDir = File(context.filesDir, "ml")
            val modelFile = File(modelDir, "gemma_2b_q4.tflite")

            if (!modelFile.exists()) {
                modelDir.mkdirs()
                try {
                    context.assets.open("ml/gemma_2b_q4.tflite").use { input ->
                        modelFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    initError = "Model file not found in assets/ml/. " +
                            "Please place gemma_2b_q4.tflite in app/src/main/assets/ml/"
                    return@withContext
                }
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(2048)
                .build()

            inference = LlmInference.createFromOptions(context, options)
            initialized = true
        } catch (e: Exception) {
            initError = "Failed to initialize LLM: ${e.message}"
        }
    }

    suspend fun generate(prompt: String): String? {
        if (!initialized) {
            initialize()
        }
        if (!initialized) {
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val engine = inference ?: return@withContext "Error: LLM not initialized"
                val result = StringBuilder()
                val latch = CountDownLatch(1)
                var genError: String? = null

                val future = engine.generateResponseAsync(
                    prompt,
                    ProgressListener { partialResult: String, done: Boolean ->
                        result.append(partialResult)
                        if (done) {
                            latch.countDown()
                        }
                    }
                )

                if (!latch.await(90, TimeUnit.SECONDS)) {
                    future.cancel(true)
                    return@withContext "Error: Generation timed out after 90 seconds"
                }

                genError?.let { return@withContext it }
                result.toString()
            } catch (e: Exception) {
                "Error during generation: ${e.message}"
            }
        }
    }

    fun close() {
        inference?.close()
        inference = null
        initialized = false
    }
}
