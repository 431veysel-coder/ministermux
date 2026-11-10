package com.ministermux

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesKey
import androidx.datastore.preferences.rx.RxPreferenceDataStoreBuilder
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import com.ministermux.bridge.MinisBridgeService
import com.ministermux.util.BootstrapExtractor
import com.ministermux.util.PermissionManager
import com.ministermux.util.TermuxEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MinistermuxApplication : Application() {

    companion object {
        const val TAG = "Ministermux"
        const val PREF_BOOTSTRAP_DONE = "bootstrap_done"
        const val PREF_BOOTSTRAP_VERSION = "bootstrap_version"
        const val BOOTSTRAP_VERSION = 1
    }

    private val dataStore by lazy {
        RxPreferenceDataStoreBuilder(this, "ministermux_prefs").build()
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Termux environment early
        TermuxEnvironment.initialize(this)
        
        // Start bootstrap extraction if needed
        checkAndExtractBootstrap()
        
        // Request permissions
        PermissionManager.requestAllPermissions(this)
        
        // Start bridge service
        startMinisBridgeService()
        
        Log.i(TAG, "Ministermux Application started")
    }

    private fun checkAndExtractBootstrap() {
        val prefs = getSharedPreferences("ministermux", Context.MODE_PRIVATE)
        val bootstrapDone = prefs.getBoolean(PREF_BOOTSTRAP_DONE, false)
        val bootstrapVersion = prefs.getInt(PREF_BOOTSTRAP_VERSION, 0)
        
        if (!bootstrapDone || bootstrapVersion < BOOTSTRAP_VERSION) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    BootstrapExtractor.extract(this@MinistermuxApplication)
                    prefs.edit()
                        .putBoolean(PREF_BOOTSTRAP_DONE, true)
                        .putInt(PREF_BOOTSTRAP_VERSION, BOOTSTRAP_VERSION)
                        .apply()
                    Log.i(TAG, "Bootstrap extracted successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Bootstrap extraction failed", e)
                }
            }
        }
    }

    private fun startMinisBridgeService() {
        val intent = Intent(this, MinisBridgeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    fun getDataStore() = dataStore
}