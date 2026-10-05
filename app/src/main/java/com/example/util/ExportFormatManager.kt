package com.example.util

import android.content.Context
import com.example.data.model.ExportFormatConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manajer Format Ekspor Laporan Keuangan FinSmart.
 *
 * Persistensi:
 * Disimpan secara lokal pada aplikasi (Application-Local Data via SharedPreferences).
 *
 * Catatan Arsitektur DEF-03:
 * Konfigurasi format ekspor ini sengaja di-scope per instalasi lokal aplikasi,
 * TIDAK memodifikasi tabel transaksi atau Room database, sehingga 100% bebas dari
 * risiko kebocoran data antar-akun (cross-account financial isolation safety).
 */
object ExportFormatManager {
    private const val PREFS_NAME = "finsmart_export_formats"
    private const val KEY_CUSTOM_FORMATS = "custom_formats_json"
    private const val KEY_ACTIVE_FORMAT_ID = "active_format_id"

    fun getAllFormats(context: Context): List<ExportFormatConfig> {
        val result = mutableListOf<ExportFormatConfig>()
        // Built-in formats (protected from deletion)
        result.add(ExportFormatConfig.BUILTIN_PDF)
        result.add(ExportFormatConfig.BUILTIN_CSV)

        // Custom formats from SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CUSTOM_FORMATS, null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val colsArray = obj.optJSONArray("columns")
                    val colsList = mutableListOf<String>()
                    if (colsArray != null) {
                        for (c in 0 until colsArray.length()) {
                            colsList.add(colsArray.getString(c))
                        }
                    } else {
                        colsList.addAll(ExportFormatConfig.DEFAULT_COLUMNS)
                    }

                    result.add(
                        ExportFormatConfig(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            type = obj.optString("type", "CSV"),
                            isBuiltIn = false,
                            delimiter = obj.optString("delimiter", ","),
                            dateFormat = obj.optString("dateFormat", "yyyy-MM-dd HH:mm:ss"),
                            columns = colsList,
                            includeHeader = obj.optBoolean("includeHeader", true)
                        )
                    )
                }
            } catch (_: Exception) {
                // Ignore parse errors, keep built-in formats
            }
        }
        return result
    }

    fun saveFormat(context: Context, format: ExportFormatConfig): Boolean {
        // Built-in formats cannot be overwritten as custom
        if (format.id == ExportFormatConfig.BUILTIN_PDF.id || format.id == ExportFormatConfig.BUILTIN_CSV.id) {
            return false
        }

        val allCustom = getCustomFormats(context).toMutableList()
        val index = allCustom.indexOfFirst { it.id == format.id }
        if (index != -1) {
            allCustom[index] = format
        } else {
            allCustom.add(format)
        }
        saveCustomFormats(context, allCustom)
        return true
    }

    fun deleteFormat(context: Context, formatId: String): Boolean {
        // Built-in formats protected
        if (formatId == ExportFormatConfig.BUILTIN_PDF.id || formatId == ExportFormatConfig.BUILTIN_CSV.id) {
            return false
        }
        val allCustom = getCustomFormats(context).toMutableList()
        val removed = allCustom.removeAll { it.id == formatId }
        if (removed) {
            saveCustomFormats(context, allCustom)
            // If active format was deleted, reset to default PDF
            if (getActiveFormatId(context) == formatId) {
                setActiveFormatId(context, ExportFormatConfig.BUILTIN_PDF.id)
            }
        }
        return removed
    }

    fun getActiveFormatId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_FORMAT_ID, ExportFormatConfig.BUILTIN_PDF.id) ?: ExportFormatConfig.BUILTIN_PDF.id
    }

    fun setActiveFormatId(context: Context, formatId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_FORMAT_ID, formatId).apply()
    }

    private fun getCustomFormats(context: Context): List<ExportFormatConfig> {
        return getAllFormats(context).filter { !it.isBuiltIn }
    }

    private fun saveCustomFormats(context: Context, formats: List<ExportFormatConfig>) {
        val array = JSONArray()
        formats.forEach { f ->
            val obj = JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("type", f.type)
                put("delimiter", f.delimiter)
                put("dateFormat", f.dateFormat)
                put("includeHeader", f.includeHeader)
                val cols = JSONArray()
                f.columns.forEach { cols.put(it) }
                put("columns", cols)
            }
            array.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_FORMATS, array.toString()).apply()
    }
}
