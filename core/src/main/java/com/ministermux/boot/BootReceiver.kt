package com.ministermux.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ministermux.bridge.MinisBridgeService
import com.ministermux.util.TermuxEnvironment

class BootReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "MinistermuxBoot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_QUICKBOOT_POWERON) return
        
        Log.i(TAG, "Boot completed, starting MinisTermux services")
        
        // Start bridge service
        val bridgeIntent = Intent(context, MinisBridgeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(bridgeIntent)
        } else {
            context.startService(bridgeIntent)
        }
        
        // Run boot scripts
        BootManager.runBootScripts(context)
    }
}

object BootManager {
    private const val TAG = "BootManager"
    private const val BOOT_SCRIPTS_DIR = "boot"
    
    fun runBootScripts(context: Context) {
        val scriptsDir = File(TermuxEnvironment.HOME, ".termux/$BOOT_SCRIPTS_DIR")
        if (!scriptsDir.exists()) return
        
        val scripts = scriptsDir.listFiles()?.filter { it.name.endsWith(".sh") }?.sortedBy { it.name } ?: return
        
        for (script in scripts) {
            try {
                Log.i(TAG, "Running boot script: ${script.name}")
                val pb = ProcessBuilder("bash", script.absolutePath)
                pb.directory(File(TermuxEnvironment.HOME))
                TermuxEnvironment.getEnvMap().forEach { (k, v) -> pb.environment()[k] = v }
                pb.redirectErrorStream(true)
                val process = pb.start()
                val output = process.inputStream.readBytes().decodeToString()
                val exitCode = process.waitFor()
                Log.i(TAG, "Boot script ${script.name} exited with $exitCode: $output")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run boot script ${script.name}", e)
            }
        }
    }
    
    fun addBootScript(context: Context, scriptName: String, content: String) {
        val scriptsDir = File(TermuxEnvironment.HOME, ".termux/$BOOT_SCRIPTS_DIR")
        scriptsDir.mkdirs()
        val scriptFile = File(scriptsDir, scriptName)
        scriptFile.writeText(content)
        scriptFile.setExecutable(true, false)
    }
}