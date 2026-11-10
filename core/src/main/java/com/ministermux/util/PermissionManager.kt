package com.ministermux.util

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ministermux.util.TermuxEnvironment

object PermissionManager {
    private const val TAG = "PermissionManager"
    
    // All permissions we need
    private val ALL_PERMISSIONS = arrayOf(
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_SMS",
        "android.permission.READ_CONTACTS",
        "android.permission.READ_PHONE_STATE",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.NEARBY_WIFI_DEVICES",
        "android.permission.BODY_SENSORS"
    )
    
    // Permissions that need special handling
    private val SPECIAL_PERMISSIONS = setOf(
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.REQUEST_INSTALL_PACKAGES"
    )
    
    fun requestAllPermissions(context: Context) {
        val activity = findActivity(context) ?: return
        
        // Check which permissions are already granted
        val toRequest = ALL_PERMISSIONS.filter { permission ->
            when (permission) {
                "android.permission.MANAGE_EXTERNAL_STORAGE" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        !Environment.isExternalStorageManager()
                    } else {
                        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
                    }
                }
                "android.permission.SYSTEM_ALERT_WINDOW" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        !android.provider.Settings.canDrawOverlays(context)
                    } else {
                        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
                    }
                }
                "android.permission.REQUEST_INSTALL_PACKAGES" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val pm = context.packageManager
                        !pm.canRequestPackageInstalls()
                    } else {
                        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
                    }
                }
                else -> ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
            }
        }
        
        if (toRequest.isNotEmpty()) {
            // Request normal permissions
            val normalPermissions = toRequest.filter { it !in SPECIAL_PERMISSIONS }
            if (normalPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    activity,
                    normalPermissions.toTypedArray(),
                    1001
                )
            }
            
            // Handle special permissions
            toRequest.filter { it in SPECIAL_PERMISSIONS }.forEach { permission ->
                when (permission) {
                    "android.permission.MANAGE_EXTERNAL_STORAGE" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = android.net.Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        }
                    }
                    "android.permission.SYSTEM_ALERT_WINDOW" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            intent.data = android.net.Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        }
                    }
                    "android.permission.REQUEST_INSTALL_PACKAGES" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                            intent.data = android.net.Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        }
                    }
                }
            }
        }
    }
    
    fun areAllPermissionsGranted(context: Context): Boolean {
        return ALL_PERMISSIONS.all { permission ->
            when (permission) {
                "android.permission.MANAGE_EXTERNAL_STORAGE" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Environment.isExternalStorageManager()
                    } else {
                        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                    }
                }
                "android.permission.SYSTEM_ALERT_WINDOW" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        android.provider.Settings.canDrawOverlays(context)
                    } else {
                        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                    }
                }
                "android.permission.REQUEST_INSTALL_PACKAGES" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.packageManager.canRequestPackageInstalls()
                    } else {
                        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                    }
                }
                else -> ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
    
    private fun findActivity(context: Context): Activity? {
        var current = context
        while (current is android.content.ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}