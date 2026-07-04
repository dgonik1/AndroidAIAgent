package com.example.aiagent.ui

import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aiagent.agent.AgentLoop
import com.example.aiagent.agent.LLMEngine
import com.example.aiagent.tools.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isThinking by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val llmEngine = remember { LLMEngine(context) }
    val capturePhoto = rememberCameraCaptureHandler()
    val tools = remember {
        listOf(
            SMSTool(),
            PhoneTool(),
            CameraTool(capturePhoto),
            FileTool(),
            WebSearchTool(),
            SystemTool()
        )
    }
    val agentLoop = remember { AgentLoop(llmEngine, tools, context) }

    LaunchedEffect(Unit) {
        llmEngine.initialize()
        messages = messages + ChatMessage(
            content = "🤖 AI Agent ready!\n\n" +
                    "I can help you with:\n" +
                    "\u2022 Sending SMS messages\n" +
                    "\u2022 Making phone calls\n" +
                    "\u2022 Taking photos\n" +
                    "\u2022 Reading/writing files\n" +
                    "\u2022 Searching the web\n" +
                    "\u2022 Controlling WiFi, Bluetooth & brightness\n\n" +
                    "Try asking me something!",
            role = MessageRole.AGENT
        )
    }

    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1 + (if (isThinking) 1 else 0))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Agent", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = {
                        messages = messages + ChatMessage(
                            content = if (llmEngine.isInitialized) {
                                "✅ LLM is initialized"
                            } else {
                                "⚠️ ${llmEngine.initializationError ?: "Initializing..."}"
                            },
                            role = MessageRole.SYSTEM
                        )
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Status")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 120.dp),
                        placeholder = { Text("Ask me to do something...") },
                        shape = RoundedCornerShape(24.dp),
                        enabled = !isThinking,
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val userText = inputText.trim()
                                inputText = ""
                                messages = messages + ChatMessage(
                                    content = userText,
                                    role = MessageRole.USER
                                )
                                isThinking = true

                                scope.launch {
                                    try {
                                        agentLoop.processInput(userText).collect { event ->
                                            when (event) {
                                                is AgentEvent.Thinking -> { /* update indicator */ }
                                                is AgentEvent.ToolCall -> {
                                                    messages = messages + ChatMessage(
                                                        content = "🔧 **${event.tool}**",
                                                        role = MessageRole.SYSTEM
                                                    )
                                                }
                                                is AgentEvent.ToolResult -> {
                                                    val lastMsg = messages.lastOrNull()
                                                    if (lastMsg?.role == MessageRole.SYSTEM &&
                                                        lastMsg.content.startsWith("🔧")
                                                    ) {
                                                        messages = messages.dropLast(1) + lastMsg.copy(
                                                            content = lastMsg.content + "\n✅ Done"
                                                        )
                                                    }
                                                }
                                                is AgentEvent.Response -> {
                                                    messages = messages + ChatMessage(
                                                        content = event.content,
                                                        role = MessageRole.AGENT
                                                    )
                                                }
                                                is AgentEvent.Error -> {
                                                    messages = messages + ChatMessage(
                                                        content = "❌ ${event.message}",
                                                        role = MessageRole.SYSTEM
                                                    )
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        messages = messages + ChatMessage(
                                            content = "❌ Error: ${e.message}",
                                            role = MessageRole.SYSTEM
                                        )
                                    } finally {
                                        isThinking = false
                                    }
                                }
                            }
                        },
                        enabled = !isThinking && inputText.isNotBlank(),
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message)
            }

            if (isThinking) {
                item { ThinkingIndicator() }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val isAgent = message.role == MessageRole.AGENT
    val isSystem = message.role == MessageRole.SYSTEM

    val backgroundColor = when {
        isUser -> MaterialTheme.colorScheme.primary
        isAgent -> MaterialTheme.colorScheme.secondaryContainer
        isSystem -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimary
        isAgent -> MaterialTheme.colorScheme.onSecondaryContainer
        isSystem -> MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val shape = if (isUser) {
        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
    } else {
        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = shape,
            color = backgroundColor,
            tonalElevation = if (isSystem) 0.dp else 1.dp
        ) {
            Text(
                text = message.content,
                color = textColor,
                fontSize = if (isSystem) 12.sp else 14.sp,
                fontStyle = if (isSystem) FontStyle.Italic else FontStyle.Normal,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun ThinkingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Thinking",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                var dots by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    while (true) {
                        dots = when (dots) {
                            "" -> "."
                            "." -> ".."
                            ".." -> "..."
                            else -> ""
                        }
                        delay(500)
                    }
                }
                Text(
                    text = dots,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun rememberCameraCaptureHandler(): suspend () -> String {
    val context = LocalContext.current
    val pendingDeferred = remember { mutableStateOf<CompletableDeferred<String>?>(null) }
    val photoUri = remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val deferred = pendingDeferred.value
        val uri = photoUri.value
        if (deferred != null && uri != null) {
            if (success) {
                deferred.complete(uri.toString())
            } else {
                deferred.completeExceptionally(Exception("Camera capture was cancelled"))
            }
        }
        pendingDeferred.value = null
        photoUri.value = null
    }

    val captureBlock: suspend () -> String = suspend {
        val deferred = CompletableDeferred<String>()
        pendingDeferred.value = deferred

        val timestamp = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "agent_photo_$timestamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        )
        if (uri == null) {
            throw Exception("Failed to create photo file in MediaStore")
        }
        photoUri.value = uri

        launcher.launch(uri)
        deferred.await()
    }

    return captureBlock
}
