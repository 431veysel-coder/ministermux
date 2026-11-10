package com.ministermux.util

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File

object TermuxEnvironment {
    private const val TAG = "TermuxEnvironment"
    
    // Paths
    lateinit var PREFIX: String
    lateinit var HOME: String
    lateinit var TMPDIR: String
    lateinit var LD_LIBRARY_PATH: String
    lateinit var PATH: String
    
    fun initialize(app: Application) {
        val filesDir = app.filesDir.parentFile.absolutePath
        PREFIX = "$filesDir/usr"
        HOME = "$filesDir/home"
        TMPDIR = "$PREFIX/tmp"
        
        // Setup library path
        val libDir = File(PREFIX, "lib")
        val libDirs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            arrayOf(libDir.absolutePath, "/system/lib64", "/system/lib", "/vendor/lib64", "/vendor/lib")
        } else {
            arrayOf(libDir.absolutePath, "/system/lib", "/vendor/lib")
        }
        LD_LIBRARY_PATH = libDirs.joinToString(":")
        
        // Setup PATH
        val binDir = File(PREFIX, "bin")
        PATH = "${binDir.absolutePath}:${Environment.getEnvironment().get("PATH", "")}"
        
        // Set system properties for native code
        System.setProperty("termux.prefix", PREFIX)
        System.setProperty("termux.home", HOME)
        System.setProperty("termux.tmpdir", TMPDIR)
        
        Log.i(TAG, "Termux environment initialized: PREFIX=$PREFIX, HOME=$HOME")
    }
    
    fun setupProcessBuilder(pb: java.lang.ProcessBuilder) {
        pb.environment()["PREFIX"] = PREFIX
        pb.environment()["HOME"] = HOME
        pb.environment()["TMPDIR"] = TMPDIR
        pb.environment()["LD_LIBRARY_PATH"] = LD_LIBRARY_PATH
        pb.environment()["PATH"] = PATH
        pb.environment()["TERMUX_VERSION"] = "1.0.0"
        pb.environment()["TERMUX_APP__PACKAGE_NAME"] = "com.ministermux"
    }
    
    fun getEnvMap(): Map<String, String> = mapOf(
        "PREFIX" to PREFIX,
        "HOME" to HOME,
        "TMPDIR" to TMPDIR,
        "LD_LIBRARY_PATH" to LD_LIBRARY_PATH,
        "PATH" to PATH,
        "TERMUX_VERSION" to "1.0.0",
        "TERMUX_APP__PACKAGE_NAME" to "com.ministermux"
    )
}