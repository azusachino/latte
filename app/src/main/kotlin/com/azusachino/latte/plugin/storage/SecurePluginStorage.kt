package com.azusachino.latte.plugin.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePluginStorage(context: Context) {
    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    fun save(pluginId: String, key: String, value: String) {
        prefs.edit().putString(buildKey(pluginId, key), value).apply()
    }

    fun get(pluginId: String, key: String): String? {
        return prefs.getString(buildKey(pluginId, key), null)
    }

    fun remove(pluginId: String, key: String) {
        prefs.edit().remove(buildKey(pluginId, key)).apply()
    }

    fun clearPlugin(pluginId: String) {
        val prefix = "$pluginId:"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun getAll(pluginId: String): Map<String, String> {
        val prefix = "$pluginId:"
        val result = mutableMapOf<String, String>()
        for ((k, v) in prefs.all) {
            if (k.startsWith(prefix) && v is String) {
                result[k.removePrefix(prefix)] = v
            }
        }
        return result
    }

    private fun buildKey(pluginId: String, key: String): String = "$pluginId:$key"

    companion object {
        private const val TAG = "SecurePluginStorage"
        private const val PREFS_FILE_NAME = "latte_secure_plugin_prefs"

        private fun createEncryptedPrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize EncryptedSharedPreferences, resetting corrupted keys", e)
                try {
                    context.deleteSharedPreferences(PREFS_FILE_NAME)
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()

                    EncryptedSharedPreferences.create(
                        context,
                        PREFS_FILE_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (e2: Exception) {
                    Log.e(TAG, "Fallback to standard private preferences after Keystore failure", e2)
                    context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
                }
            }
        }
    }
}
