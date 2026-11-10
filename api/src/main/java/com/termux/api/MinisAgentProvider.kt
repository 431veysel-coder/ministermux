package com.termux.api

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.ministermux.bridge.FileBridge
import com.ministermux.util.TermuxEnvironment
import java.io.File

class MinisAgentProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.ministermux.minisagent"
        const val PATH_FILES = "files"
        const val PATH_LIST = "list"
        const val PATH_READ = "read"
        const val PATH_WRITE = "write"
    }
    
    private val MINIS_ROOT = File(TermuxEnvironment.HOME).parentFile

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val path = getPathFromUri(uri)
        
        return when (path) {
            PATH_LIST -> listFiles(uri)
            PATH_READ -> readFile(uri)
            else -> MatrixCursor(arrayOf("error")).apply { addRow(arrayOf("Unknown query")) }
        }
    }
    
    private fun listFiles(uri: Uri): MatrixCursor {
        val relPath = uri.getQueryParameter("path") ?: ""
        val result = FileBridge.list(relPath, context!!)
        
        val cursor = MatrixCursor(arrayOf("name", "is_dir", "size", "modified"))
        if (result.success) {
            @Suppress("UNCHECKED_CAST")
            val files = result.data as? List<Map<String, Any>> ?: emptyList()
            files.forEach { f ->
                cursor.addRow(arrayOf(
                    f["name"] as String,
                    (f["is_dir"] as Boolean).toString(),
                    (f["size"] as Long).toString(),
                    (f["modified"] as Long).toString()
                ))
            }
        }
        return cursor
    }
    
    private fun readFile(uri: Uri): MatrixCursor {
        val relPath = uri.getQueryParameter("path") ?: return MatrixCursor(arrayOf("error")).apply { addRow(arrayOf("Missing path")) }
        val result = FileBridge.read(relPath, context!!)
        
        val cursor = MatrixCursor(arrayOf("content"))
        if (result.success) {
            cursor.addRow(arrayOf(result.data?.toString() ?: ""))
        } else {
            cursor.addRow(arrayOf("ERROR: ${result.error}"))
        }
        return cursor
    }

    override fun getType(uri: Uri): String {
        val path = getPathFromUri(uri)
        return when (path) {
            PATH_FILES -> "vnd.android.cursor.dir/vnd.ministermux.file"
            else -> "text/plain"
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val relPath = getFilePathFromUri(uri)
        val file = resolvePath(relPath)
        
        val accessMode = when {
            mode.contains("w") -> ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE
            mode.contains("r") -> ParcelFileDescriptor.MODE_READ_ONLY
            else -> ParcelFileDescriptor.MODE_READ_ONLY
        }
        
        return ParcelFileDescriptor.open(file, accessMode)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val relPath = getFilePathFromUri(uri)
        val content = values?.getAsString("content") ?: ""
        
        val result = FileBridge.write(relPath, content, context!!)
        if (result.success) {
            return Uri.parse("content://$AUTHORITY/$PATH_FILES/$relPath")
        }
        throw IllegalStateException("Write failed: ${result.error}")
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        return insert(uri, values).let { if (it != null) 1 else 0 }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val relPath = getFilePathFromUri(uri)
        val file = resolvePath(relPath)
        file.deleteRecursively()
        return 1
    }
    
    private fun getPathFromUri(uri: Uri): String {
        val pathSegments = uri.pathSegments
        return if (pathSegments.size > 1) pathSegments[1] else ""
    }
    
    private fun getFilePathFromUri(uri: Uri): String {
        val pathSegments = uri.pathSegments
        return if (pathSegments.size > 2) pathSegments.drop(2).joinToString("/") else ""
    }
    
    private fun resolvePath(relPath: String): File {
        val normalized = relPath.replace("../", "").replace("..\\", "")
        val file = File(MINIS_ROOT, normalized)
        file.parentFile?.mkdirs()
        return file
    }
}