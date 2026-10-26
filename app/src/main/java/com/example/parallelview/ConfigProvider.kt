package com.example.parallelview

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

/**
 * Exposes the split ratio to other processes (the hook runs inside the TARGET app,
 * whose uid cannot read our SharedPreferences and needs WRITE_SECURE_SETTINGS to
 * write Settings.Global - a permission a normal APK install never gets).
 *
 * Reading is intentionally open: the value is a display ratio, not sensitive.
 */
class ConfigProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: android.os.Bundle?): android.os.Bundle {
        val out = android.os.Bundle()
        if (method == "ratio") {
            val v = ConfigStore.ratio(context)
            out.putFloat("ratio", v)
        }
        return out
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?
    ): Int = 0
}

/** Single source of truth for the ratio, stored in the module's own prefs. */
object ConfigStore {
    private const val FILE = "parallelview_config"
    private const val KEY = "ratio"
    const val DEFAULT = 0.38f

    /** Returns -1f when nothing has been configured yet, so the hook can fall
     *  back to the legacy Settings.Global value (users upgrading from 1.1.0). */
    fun ratio(context: Context?): Float {
        if (context == null) return -1f
        return try {
            val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            if (!prefs.contains(KEY)) return -1f
            prefs.getFloat(KEY, DEFAULT).coerceIn(0.15f, 0.85f)
        } catch (_: Throwable) {
            -1f
        }
    }

    fun setRatio(context: Context, v: Float) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putFloat(KEY, v.coerceIn(0.15f, 0.85f)).commit()
    }
}
