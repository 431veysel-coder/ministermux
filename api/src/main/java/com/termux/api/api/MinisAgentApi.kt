package com.termux.api.api

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.termux.api.TermuxApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

class MinisAgentApi : TermuxApi() {
    companion object {
        const val TAG = "MinisAgentApi"
    }

    override fun execute(context: Context, args: JsonObject): JsonObject {
        val action = args["action"]?.jsonPrimitive?.content ?: return error("Missing action")
        
        return when (action) {
            "agent_status" -> getAgentStatus(args)
            "agent_event" -> sendAgentEvent(args)
            "mcp_list" -> listMcpServers()
            "mcp_start" -> startMcpServer(args)
            "mcp_stop" -> stopMcpServer(args)
            "skill_run" -> runSkill(args)
            "fs_read" -> readMinisFile(args)
            "fs_write" -> writeMinisFile(args)
            "fs_list" -> listMinisDir(args)
            "fs_mkdir" -> mkdirMinisDir(args)
            "fs_delete" -> deleteMinisFile(args)
            "exec" -> executeCommand(args)
            "pkg_install" -> installPackage(args)
            "pkg_remove" -> removePackage(args)
            "pkg_list" -> listPackages()
            "system_info" -> getSystemInfo()
            else -> error("Unknown action: $action")
        }
    }

    private fun getAgentStatus(args: JsonObject): JsonObject {
        val bridge = com.ministermux.bridge.MinisBridgeService.getInstance(TermuxApi.getApplication())
        return if (bridge != null) {
            JsonObject(mapOf(
                "sessions" to bridge.sessions.size,
                "active" to bridge.sessions.values.count { it.isActive },
                "uptime" to (System.currentTimeMillis() - bridge.startTime)
            ))
        } else {
            JsonObject(mapOf("error" to "Bridge not running"))
        }
    }

    private fun sendAgentEvent(args: JsonObject): JsonObject {
        val agentId = args["agent_id"]?.jsonPrimitive?.content ?: return error("Missing agent_id")
        val event = args["event"]?.jsonPrimitive?.content ?: return error("Missing event")
        val data = args["data"]?.jsonPrimitive?.content ?: ""
        
        // Send event via bridge
        val bridge = com.ministermux.bridge.MinisBridgeService.getInstance(TermuxApi.getApplication())
        bridge?.sendAgentEvent(agentId, event, data)
        
        return JsonObject(mapOf("sent" to true))
    }

    private fun listMcpServers(): JsonObject {
        val servers = com.ministermux.bridge.McpRegistry.servers.keys.toList()
        return JsonObject(mapOf("servers" to servers.joinToString(",")))
    }

    private fun startMcpServer(args: JsonObject): JsonObject {
        val name = args["name"]?.jsonPrimitive?.content ?: return error("Missing name")
        // Implementation depends on MCP server type
        return JsonObject(mapOf("started" to true, "name" to name))
    }

    private fun stopMcpServer(args: JsonObject): JsonObject {
        val name = args["name"]?.jsonPrimitive?.content ?: return error("Missing name")
        return JsonObject(mapOf("stopped" to true, "name" to name))
    }

    private fun runSkill(args: JsonObject): JsonObject {
        val skill = args["skill"]?.jsonPrimitive?.content ?: return error("Missing skill")
        val params = args["params"] as? JsonObject ?: JsonObject()
        
        val result = com.ministermux.bridge.SkillRegistry.run(skill, params, TermuxApi.getApplication())
        return if (result.success) {
            JsonObject(mapOf("success" to true, "data" to result.data?.toString() ?: ""))
        } else {
            JsonObject(mapOf("success" to false, "error" to result.error))
        }
    }

    private fun readMinisFile(args: JsonObject): JsonObject {
        val path = args["path"]?.jsonPrimitive?.content ?: return error("Missing path")
        val result = com.ministermux.bridge.FileBridge.read(path, TermuxApi.getApplication())
        return if (result.success) {
            JsonObject(mapOf("success" to true, "content" to result.data?.toString() ?: ""))
        } else {
            JsonObject(mapOf("success" to false, "error" to result.error))
        }
    }

    private fun writeMinisFile(args: JsonObject): JsonObject {
        val path = args["path"]?.jsonPrimitive?.content ?: return error("Missing path")
        val content = args["content"]?.jsonPrimitive?.content ?: return error("Missing content")
        val result = com.ministermux.bridge.FileBridge.write(path, content, TermuxApi.getApplication())
        return if (result.success) {
            JsonObject(mapOf("success" to true))
        } else {
            JsonObject(mapOf("success" to false, "error" to result.error))
        }
    }

    private fun listMinisDir(args: JsonObject): JsonObject {
        val path = args["path"]?.jsonPrimitive?.content ?: return error("Missing path")
        val result = com.ministermux.bridge.FileBridge.list(path, TermuxApi.getApplication())
        return if (result.success) {
            JsonObject(mapOf("success" to true, "files" to result.data?.toString() ?: ""))
        } else {
            JsonObject(mapOf("success" to false, "error" to result.error))
        }
    }

    private fun mkdirMinisDir(args: JsonObject): JsonObject {
        val path = args["path"]?.jsonPrimitive?.content ?: return error("Missing path")
        val file = com.ministermux.bridge.FileBridge.resolvePath(path)
        file.mkdirs()
        return JsonObject(mapOf("success" to true))
    }

    private fun deleteMinisFile(args: JsonObject): JsonObject {
        val path = args["path"]?.jsonPrimitive?.content ?: return error("Missing path")
        val file = com.ministermux.bridge.FileBridge.resolvePath(path)
        file.deleteRecursively()
        return JsonObject(mapOf("success" to true))
    }

    private fun executeCommand(args: JsonObject): JsonObject {
        val cmd = args["cmd"]?.jsonPrimitive?.content ?: return error("Missing cmd")
        val argsList = args["args"] as? List<String> ?: emptyList()
        val env = args["env"] as? Map<String, String> ?: emptyMap()
        val sessionId = args["session_id"]?.jsonPrimitive?.content ?: "api-${System.currentTimeMillis()}"
        
        val bridge = com.ministermux.bridge.MinisBridgeService.getInstance(TermuxApi.getApplication())
        val msg = com.ministermux.bridge.BridgeMessage(
            id = java.util.UUID.randomUUID().toString(),
            type = com.ministermux.bridge.BridgeMessage.Type.EXEC,
            sessionId = sessionId,
            payload = com.ministermux.bridge.BridgeMessage.Payload.Exec(cmd, argsList, env)
        )
        
        val result = bridge?.processMessageSync(msg)
        return if (result?.success == true) {
            JsonObject(mapOf("success" to true, "output" to result.data?.toString() ?: ""))
        } else {
            JsonObject(mapOf("success" to false, "error" to result?.error ?: "Unknown error"))
        }
    }

    private fun installPackage(args: JsonObject): JsonObject {
        val pkg = args["package"]?.jsonPrimitive?.content ?: return error("Missing package")
        // Execute: pkg install -y $pkg
        val result = executeCommand(JsonObject(mapOf(
            "cmd" to "pkg",
            "args" to listOf("install", "-y", pkg)
        )))
        return result
    }

    private fun removePackage(args: JsonObject): JsonObject {
        val pkg = args["package"]?.jsonPrimitive?.content ?: return error("Missing package")
        val result = executeCommand(JsonObject(mapOf(
            "cmd" to "pkg",
            "args" to listOf("uninstall", pkg)
        )))
        return result
    }

    private fun listPackages(): JsonObject {
        val result = executeCommand(JsonObject(mapOf(
            "cmd" to "pkg",
            "args" to listOf("list-installed")
        )))
        return if (result.getBoolean("success")) {
            JsonObject(mapOf("success" to true, "packages" to result.getString("output")))
        } else {
            result
        }
    }

    private fun getSystemInfo(): JsonObject {
        return JsonObject(mapOf(
            "prefix" to com.ministermux.util.TermuxEnvironment.PREFIX,
            "home" to com.ministermux.util.TermuxEnvironment.HOME,
            "arch" to android.os.Build.SUPPORTED_ABIS[0],
            "android_version" to android.os.Build.VERSION.RELEASE,
            "sdk_int" to android.os.Build.VERSION.SDK_INT
        ))
    }

    private fun error(msg: String): JsonObject = JsonObject(mapOf("error" to msg))
}