package com.termux.api

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class TermuxApiReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TERMUX_API = "com.termux.api.TERMUX_API"
        const val EXTRA_SOCKET_INPUT = "socket_input"
        const val EXTRA_SOCKET_OUTPUT = "socket_output"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TERMUX_API) return
        
        val inputSocketName = intent.getStringExtra(EXTRA_SOCKET_INPUT)
        val outputSocketName = intent.getStringExtra(EXTRA_SOCKET_OUTPUT)
        
        if (inputSocketName == null || outputSocketName == null) {
            Log.e("TermuxApiReceiver", "Missing socket names")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            handleRpcConnection(inputSocketName, outputSocketName)
        }
    }
    
    private suspend fun handleRpcConnection(inputName: String, outputName: String) {
        try {
            val inputServer = LocalServerSocket(inputName)
            val outputServer = LocalServerSocket(outputName)
            
            val inputSocket = inputServer.accept()
            val outputSocket = outputServer.accept()
            
            val reader = BufferedReader(InputStreamReader(inputSocket.inputStream))
            val writer = OutputStreamWriter(outputSocket.outputStream)
            
            while (true) {
                val line = reader.readLine() ?: break
                val request = parseRequest(line)
                val response = processRequest(request)
                writer.write(response + "\n")
                writer.flush()
            }
        } catch (e: Exception) {
            Log.e("TermuxApiReceiver", "RPC error", e)
        }
    }
    
    private fun parseRequest(line: String): RpcRequest {
        // Parse JSON-RPC or simple command format
        return try {
            val json = kotlinx.serialization.json.Json.decodeFromString<RpcRequest>(line)
            json
        } catch (e: Exception) {
            // Simple format: api_name method arg1 arg2...
            val parts = line.split(" ")
            RpcRequest(parts.firstOrNull() ?: "", parts.getOrNull(1) ?: "", parts.drop(2))
        }
    }
    
    private fun processRequest(request: RpcRequest): String {
        val result = TermuxApiManager.call(request.apiName, buildArgs(request), com.termux.api.TermuxApi.getApplication())
        return kotlinx.serialization.json.Json.encodeToString(result)
    }
    
    private fun buildArgs(request: RpcRequest): kotlinx.serialization.json.JsonObject {
        val obj = kotlinx.serialization.json.JsonObject()
        request.args.forEachIndexed { index, arg ->
            obj.put("arg$index", kotlinx.serialization.json.JsonPrimitive(arg))
        }
        return obj
    }
    
    data class RpcRequest(
        val apiName: String,
        val method: String,
        val args: List<String>
    )
}