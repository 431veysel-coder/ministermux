package com.ministermux.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ministermux.util.TermuxEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RunCommandReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_RUN_COMMAND = "com.ministermux.RUN_COMMAND"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_ARGS = "args"
        const val EXTRA_WORKING_DIR = "working_dir"
        const val EXTRA_ENV = "env"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_CALLBACK_ACTION = "callback_action"
        const val EXTRA_CALLBACK_PACKAGE = "callback_package"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RUN_COMMAND) return
        
        val command = intent.getStringExtra(EXTRA_COMMAND) ?: return
        val args = intent.getStringArrayListExtra(EXTRA_ARGS) ?: emptyList()
        val workingDir = intent.getStringExtra(EXTRA_WORKING_DIR) ?: TermuxEnvironment.HOME
        val env = intent.getSerializableExtra(EXTRA_ENV) as? Map<String, String> ?: emptyMap()
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: "intent-${System.currentTimeMillis()}"
        val callbackAction = intent.getStringExtra(EXTRA_CALLBACK_ACTION)
        val callbackPackage = intent.getStringExtra(EXTRA_CALLBACK_PACKAGE)
        
        CoroutineScope(Dispatchers.IO).launch {
            val bridge = MinisBridgeService.getInstance(context) ?: return@launch
            val msg = BridgeMessage(
                id = java.util.UUID.randomUUID().toString(),
                type = BridgeMessage.Type.EXEC,
                sessionId = sessionId,
                payload = BridgeMessage.Payload.Exec(command, args, env)
            )
            
            val result = bridge.processMessageSync(msg)
            
            // Send callback if requested
            if (callbackAction != null) {
                val callbackIntent = Intent(callbackAction).apply {
                    putExtra("request_id", msg.id)
                    putExtra("success", result.success)
                    putExtra("data", result.data?.toString() ?: "")
                    putExtra("error", result.error ?: "")
                    if (callbackPackage != null) {
                        setPackage(callbackPackage)
                    }
                }
                context.sendBroadcast(callbackIntent)
            }
        }
    }
}