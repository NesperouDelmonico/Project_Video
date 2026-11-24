package com.nesperoudelmonico.projectvideo.diagnostics

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Guarda el reporte como archivo en Descargas/ProjectVideo (MediaStore, sin permisos extra). */
object ReportStorage {

    data class SavedReport(val uri: Uri, val displayPath: String)

    fun saveToDownloads(context: Context, text: String): SavedReport {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "diagnostico-camera2-$stamp.txt"
        val folder = Environment.DIRECTORY_DOWNLOADS + "/ProjectVideo"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("no se pudo crear el archivo en Descargas")
        resolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("no se pudo escribir el archivo")
        return SavedReport(uri, "Descargas/ProjectVideo/$name")
    }
}
