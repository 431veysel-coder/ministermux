package com.ministermux.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipEntry

object BootstrapExtractor {
    private const val TAG = "BootstrapExtractor"
    
    fun extract(context: Context) {
        val arch = getArch()
        val assetName = "ministermux-bootstrap-$arch.zip"
        
        val assetManager = context.assets
        val inputStream = assetManager.open("bootstrap/$assetName") ?: 
            throw RuntimeException("Bootstrap asset not found: $assetName")
        
        val destDir = File(context.filesDir.parentFile, "usr")
        destDir.mkdirs()
        
        val tempFile = File(context.cacheDir, assetName)
        tempFile.deleteOnExit()
        
        // Copy asset to temp file (faster for large zip)
        FileOutputStream(tempFile).use { output ->
            inputStream.copyTo(output)
        }
        
        // Extract
        ZipFile(tempFile).use { zipFile ->
            zipFile.entries().forEachRemaining { entry ->
                val entryFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    entryFile.mkdirs()
                } else {
                    entryFile.parentFile?.mkdirs()
                    zipFile.getInputStream(entry).use { input ->
                        FileOutputStream(entryFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    // Preserve executable permissions
                    if (entry.name.endsWith(".so") || entry.name.contains("/bin/")) {
                        entryFile.setExecutable(true, false)
                    }
                }
            }
        }
        
        tempFile.delete()
        
        // Setup symlinks and permissions
        setupEnvironment(context, destDir)
        
        Log.i(TAG, "Bootstrap extracted to $destDir")
    }
    
    private fun getArch(): String {
        return when (Build.SUPPORTED_ABIS[0]) {
            "arm64-v8a" -> "aarch64"
            "armeabi-v7a" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> "aarch64"
        }
    }
    
    private fun setupEnvironment(context: Context, prefixDir: File) {
        // Create necessary directories
        listOf("bin", "lib", "include", "share", "etc", "tmp", "var", "home").forEach { dir ->
            File(prefixDir, dir).mkdirs()
        }
        
        // Create home symlink
        val homeDir = File(context.filesDir.parentFile, "home")
        if (!homeDir.exists()) {
            homeDir.mkdirs()
        }
        
        // Setup storage symlinks (Scoped Storage compatible)
        val storageDir = File(homeDir, "storage")
        storageDir.mkdirs()
        
        val sharedDir = File(Environment.getExternalStorageDirectory())
        if (sharedDir.exists()) {
            File(storageDir, "shared").apply {
                if (!exists()) {
                    try {
                        os.symlink(sharedDir.absolutePath, absolutePath)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not create shared storage symlink", e)
                    }
                }
            }
        }
        
        // Make termux-exec executable
        File(prefixDir, "bin/termux-exec").setExecutable(true, false)
        File(prefixDir, "bin/termux-pty-helper").setExecutable(true, false)
    }
}