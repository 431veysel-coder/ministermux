package com.termux.api

import android.app.Application
import android.content.Context
import android.util.Log

open class TermuxApi {
    companion object {
        private var application: Application? = null
        
        fun setApplication(app: Application) {
            application = app
        }
        
        fun getApplication(): Application = application 
            ?: throw IllegalStateException("TermuxApi application not initialized. Call TermuxApi.setApplication() first.")
    }
    
    open fun execute(context: Context, args: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject {
        return kotlinx.serialization.json.JsonObject(mapOf("error" to "Not implemented"))
    }
}

class TermuxApiManager {
    companion object {
        private val apis = mutableMapOf<String, TermuxApi>()
        
        fun register(name: String, api: TermuxApi) {
            apis[name] = api
        }
        
        fun get(name: String): TermuxApi? = apis[name]
        
        fun call(name: String, args: kotlinx.serialization.json.JsonObject, context: Context): kotlinx.serialization.json.JsonObject {
            return get(name)?.execute(context, args) 
                ?: kotlinx.serialization.json.JsonObject(mapOf("error" to "API not found: $name"))
        }
    }
}

// Register built-in APIs
object TermuxApiInitializer {
    @JvmStatic
    fun initialize() {
        TermuxApiManager.register("minis_agent", com.termux.api.api.MinisAgentApi())
        // Other APIs registered here...
    }
}