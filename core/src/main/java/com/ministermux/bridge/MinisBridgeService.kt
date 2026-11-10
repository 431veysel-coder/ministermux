package com.ministermux.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ministermux.util.TermuxEnvironment
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

class MinisBridgeService : Service() {
    companion object {
        const val TAG = "MinisBridge"
        const val WEBSOCKET_PORT = 8765
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "ministermux_bridge"
    }

    private val sessions = ConcurrentHashMap<String, AgentSession>()
    private val messageQueue = Channel<BridgeMessage>(Channel.UNLIMITED)
    private var webSocketJob: Job? = null
    private var httpClient: HttpClient? = null
    private var isRunning = false
    
    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        initializeHttpClient()
        startMessageProcessor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        webSocketJob?.cancel()
        httpClient?.close()
        sessions.values.forEach { it.close() }
        stopForeground(true)
        super.onDestroy()
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MinisTermux Bridge",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "WebSocket bridge for Minis agent communication" }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MinisTermux Bridge")
            .setContentText("WebSocket bridge active on port $WEBSOCKET_PORT")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun initializeHttpClient() {
        httpClient = HttpClient(OkHttp) {
            install(Logging) {
                level = LogLevel.NONE
            }
            install(WebSockets)
            install(JsonFeature) {
                serializer = KotlinxSerializer(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    private fun startMessageProcessor() {
        isRunning = true
        webSocketJob = CoroutineScope(Dispatchers.IO).launch {
            while (isRunning) {
                try {
                    val msg = messageQueue.receive()
                    processMessage(msg)
                } catch (e: Exception) {
                    Log.e(TAG, "Message processor error", e)
                }
            }
        }
    }

    private suspend fun processMessage(msg: BridgeMessage) {
        when (msg.type) {
            BridgeMessage.Type.EXEC -> handleExec(msg)
            BridgeMessage.Type.API_CALL -> handleApiCall(msg)
            BridgeMessage.Type.MCP_CALL -> handleMcpCall(msg)
            BridgeMessage.Type.SKILL_RUN -> handleSkillRun(msg)
            BridgeMessage.Type.FS_READ -> handleFsRead(msg)
            BridgeMessage.Type.FS_WRITE -> handleFsWrite(msg)
            BridgeMessage.Type.FS_LIST -> handleFsList(msg)
            BridgeMessage.Type.GET_STATUS -> handleGetStatus(msg)
            BridgeMessage.Type.CREATE_SESSION -> handleCreateSession(msg)
            BridgeMessage.Type.CLOSE_SESSION -> handleCloseSession(msg)
        }
    }

    // ===== Message Handlers =====
    
    private suspend fun handleExec(msg: BridgeMessage) {
        val sessionId = msg.sessionId ?: "default"
        val session = getOrCreateSession(sessionId)
        val result = session.execute(msg.payload.cmd, msg.payload.args, msg.payload.env)
        sendResponse(msg.id, result)
    }

    private suspend fun handleApiCall(msg: BridgeMessage) {
        val apiName = msg.payload.apiName
        val args = msg.payload.args
        
        val result = try {
            TermuxApiManager.call(apiName, args, this)
        } catch (e: Exception) {
            BridgeResult.error("API call failed: ${e.message}")
        }
        sendResponse(msg.id, result)
    }

    private suspend fun handleMcpCall(msg: BridgeMessage) {
        val server = msg.payload.server
        val tool = msg.payload.tool
        val args = msg.payload.args
        
        val result = McpRegistry.call(server, tool, args, this)
        sendResponse(msg.id, result)
    }

    private suspend fun handleSkillRun(msg: BridgeMessage) {
        val skill = msg.payload.skill
        val params = msg.payload.params
        
        val result = SkillRegistry.run(skill, params, this)
        sendResponse(msg.id, result)
    }

    private suspend fun handleFsRead(msg: BridgeMessage) {
        val path = msg.payload.path
        val result = FileBridge.read(path, this)
        sendResponse(msg.id, result)
    }

    private suspend fun handleFsWrite(msg: BridgeMessage) {
        val path = msg.payload.path
        val content = msg.payload.content
        val result = FileBridge.write(path, content, this)
        sendResponse(msg.id, result)
    }

    private suspend fun handleFsList(msg: BridgeMessage) {
        val path = msg.payload.path
        val result = FileBridge.list(path, this)
        sendResponse(msg.id, result)
    }

    private suspend fun handleGetStatus(msg: BridgeMessage) {
        val status = BridgeResult.success(mapOf(
            "sessions" to sessions.size,
            "active" to sessions.values.count { it.isActive },
            "bridge_uptime" to (System.currentTimeMillis() - startTime),
            "version" to "1.0.0"
        ))
        sendResponse(msg.id, status)
    }

    private suspend fun handleCreateSession(msg: BridgeMessage) {
        val sessionId = msg.payload.sessionId ?: "session-${System.currentTimeMillis()}"
        val workingDir = msg.payload.workingDir ?: TermuxEnvironment.HOME
        val session = AgentSession(sessionId, workingDir, this)
        sessions[sessionId] = session
        sendResponse(msg.id, BridgeResult.success(mapOf("session_id" to sessionId)))
    }

    private suspend fun handleCloseSession(msg: BridgeMessage) {
        val sessionId = msg.sessionId
        sessions.remove(sessionId)?.close()
        sendResponse(msg.id, BridgeResult.success(mapOf("closed" to true)))
    }

    // ===== Utilities =====
    
    private fun getOrCreateSession(sessionId: String): AgentSession {
        return sessions.getOrPut(sessionId) {
            AgentSession(sessionId, TermuxEnvironment.HOME, this)
        }
    }

    private fun sendResponse(requestId: String, result: BridgeResult) {
        // Send via WebSocket if connected, otherwise queue for HTTP callback
        webSocketSession?.send(Frame.Text(Json.encodeToString(
            BridgeResponse(id = requestId, result = result)
        )))
    }

    fun enqueueMessage(msg: BridgeMessage) = messageQueue.trySend(msg)

    // WebSocket handling
    private var webSocketSession: DefaultClientWebSocketSession? = null
    private val startTime = System.currentTimeMillis()

    suspend fun connectWebSocket(url: String) {
        httpClient?.webSocket(url) { session ->
            webSocketSession = session
            session.incoming.consumeEach { frame ->
                when (frame) {
                    is Frame.Text -> handleWebSocketMessage(frame.readText())
                    is Frame.Binary -> { /* handle binary */ }
                    is Frame.Close -> { webSocketSession = null }
                    is Frame.Pong -> { }
                }
            }
        }
    }

    private fun handleWebSocketMessage(text: String) {
        try {
            val msg = Json.decodeFromString<BridgeMessage>(text)
            messageQueue.trySend(msg)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid WebSocket message", e)
        }
    }
}

// ===== Data Classes =====

@Serializable
sealed class BridgeMessage {
    @Serializable
    abstract class Type(val value: String) {
        object EXEC : Type("exec")
        object API_CALL : Type("api_call")
        object MCP_CALL : Type("mcp_call")
        object SKILL_RUN : Type("skill_run")
        object FS_READ : Type("fs_read")
        object FS_WRITE : Type("fs_write")
        object FS_LIST : Type("fs_list")
        object GET_STATUS : Type("get_status")
        object CREATE_SESSION : Type("create_session")
        object CLOSE_SESSION : Type("close_session")
    }
    
    @SerialName("id")
    abstract val id: String
    
    @SerialName("type")
    abstract val type: Type
    
    @SerialName("session_id")
    val sessionId: String? = null
    
    @SerialName("payload")
    abstract val payload: Payload
    
    @Serializable
    sealed class Payload {
        @Serializable data class Exec(
            val cmd: String,
            val args: List<String> = emptyList(),
            val env: Map<String, String> = emptyMap()
        ) : Payload()
        
        @Serializable data class ApiCall(
            val apiName: String,
            val args: JsonObject = JsonObject()
        ) : Payload()
        
        @Serializable data class McpCall(
            val server: String,
            val tool: String,
            val args: JsonObject = JsonObject()
        ) : Payload()
        
        @Serializable data class SkillRun(
            val skill: String,
            val params: JsonObject = JsonObject()
        ) : Payload()
        
        @Serializable data class FsRead(val path: String) : Payload()
        @Serializable data class FsWrite(val path: String, val content: String) : Payload()
        @Serializable data class FsList(val path: String) : Payload()
        @Serializable data class GetStatus() : Payload()
        @Serializable data class CreateSession(
            val sessionId: String? = null,
            val workingDir: String? = null
        ) : Payload()
        @Serializable data class CloseSession() : Payload()
    }
}

@Serializable
sealed class BridgeResult {
    @SerialName("success")
    abstract val success: Boolean
    
    @SerialName("data")
    val data: Any? = null
    
    @SerialName("error")
    val error: String? = null
    
    @Serializable
    data class Success(override val data: Any?) : BridgeResult() {
        override val success = true
    }
    
    @Serializable
    data class Error(override val error: String) : BridgeResult() {
        override val success = false
    }
    
    companion object {
        fun success(data: Any? = null) = Success(data)
        fun error(error: String) = Error(error)
    }
}

@Serializable
data class BridgeResponse(
    val id: String,
    val result: BridgeResult
)

// ===== Agent Session =====

class AgentSession(
    val sessionId: String,
    private var workingDir: String,
    private val context: Context
) {
    private var process: Process? = null
    private var isRunning = false
    
    fun execute(cmd: String, args: List<String>, env: Map<String, String>): BridgeResult {
        if (isRunning) {
            return BridgeResult.error("Session busy")
        }
        
        isRunning = true
        return try {
            val pb = ProcessBuilder(listOf(cmd) + args)
            pb.directory(File(workingDir))
            TermuxEnvironment.getEnvMap().forEach { (k, v) -> pb.environment()[k] = v }
            env.forEach { (k, v) -> pb.environment()[k] = v }
            pb.redirectErrorStream(true)
            
            process = pb.start()
            
            val output = process.inputStream.readBytes().decodeToString()
            val exitCode = process.waitFor()
            
            BridgeResult.success(mapOf(
                "output" to output,
                "exit_code" to exitCode
            ))
        } catch (e: Exception) {
            BridgeResult.error("Execution failed: ${e.message}")
        } finally {
            isRunning = false
            process = null
        }
    }
    
    fun close() {
        process?.destroyForcibly()
        process = null
        isRunning = false
    }
    
    val isActive: Boolean get() = isRunning
}

// ===== Termux API Manager =====

object TermuxApiManager {
    fun call(apiName: String, args: JsonObject, context: Context): BridgeResult {
        return try {
            val apiClass = Class.forName("com.termux.api.api.${apiName}Api")
            val method = apiClass.getMethod("execute", Context::class.java, JsonObject::class.java)
            val result = method.invoke(null, context, args) as JsonObject
            BridgeResult.success(result)
        } catch (e: Exception) {
            BridgeResult.error("API $apiName failed: ${e.message}")
        }
    }
}

// ===== MCP Registry =====

object McpRegistry {
    private val servers = mutableMapOf<String, McpServer>()
    
    fun register(name: String, server: McpServer) {
        servers[name] = server
    }
    
    suspend fun call(serverName: String, tool: String, args: JsonObject, context: Context): BridgeResult {
        val server = servers[serverName] ?: return BridgeResult.error("MCP server not found: $serverName")
        return server.call(tool, args, context)
    }
}

interface McpServer {
    suspend fun call(tool: String, args: JsonObject, context: Context): BridgeResult
}

// ===== Skill Registry =====

object SkillRegistry {
    private val skills = mutableMapOf<String, Skill>()
    
    fun register(name: String, skill: Skill) {
        skills[name] = skill
    }
    
    suspend fun run(skillName: String, params: JsonObject, context: Context): BridgeResult {
        val skill = skills[skillName] ?: return BridgeResult.error("Skill not found: $skillName")
        return skill.run(params, context)
    }
}

interface Skill {
    suspend fun run(params: JsonObject, context: Context): BridgeResult
}

// ===== File Bridge =====

object FileBridge {
    private val MINIS_ROOT = File(TermuxEnvironment.HOME).parentFile
    
    fun read(relPath: String, context: Context): BridgeResult {
        return try {
            val file = resolvePath(relPath)
            if (!file.exists()) return BridgeResult.error("File not found: $relPath")
            val content = file.readText()
            BridgeResult.success(mapOf("content" to content))
        } catch (e: Exception) {
            BridgeResult.error("Read failed: ${e.message}")
        }
    }
    
    fun write(relPath: String, content: String, context: Context): BridgeResult {
        return try {
            val file = resolvePath(relPath)
            file.parentFile?.mkdirs()
            file.writeText(content)
            BridgeResult.success(mapOf("written" to true))
        } catch (e: Exception) {
            BridgeResult.error("Write failed: ${e.message}")
        }
    }
    
    fun list(relPath: String, context: Context): BridgeResult {
        return try {
            val file = resolvePath(relPath)
            if (!file.exists() || !file.isDirectory) return BridgeResult.error("Not a directory: $relPath")
            val files = file.listFiles()?.map { f ->
                mapOf(
                    "name" to f.name,
                    "is_dir" to f.isDirectory,
                    "size" to f.length(),
                    "modified" to f.lastModified()
                )
            } ?: emptyList()
            BridgeResult.success(mapOf("files" to files))
        } catch (e: Exception) {
            BridgeResult.error("List failed: ${e.message}")
        }
    }
    
    private fun resolvePath(relPath: String): File {
        // Security: prevent directory traversal
        val normalized = relPath.replace("../", "").replace("..\\", "")
        return File(MINIS_ROOT, normalized).canonicalFile
    }
}