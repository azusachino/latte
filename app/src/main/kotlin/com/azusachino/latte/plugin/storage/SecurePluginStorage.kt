package com.azusachino.latte.plugin.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface PluginStorage {
    fun save(pluginId: String, key: String, value: String)
    fun get(pluginId: String, key: String): String?
    fun remove(pluginId: String, key: String)
    fun clearPlugin(pluginId: String)
    fun getAll(pluginId: String): Map<String, String> = emptyMap()
}

class SecurePluginStorage(context: Context) : PluginStorage {
    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    override fun save(pluginId: String, key: String, value: String) {
        prefs.edit().putString(buildKey(pluginId, key), value).apply()
    }

    override fun get(pluginId: String, key: String): String? {
        return prefs.getString(buildKey(pluginId, key), null)
    }

    override fun remove(pluginId: String, key: String) {
        prefs.edit().remove(buildKey(pluginId, key)).apply()
    }

    override fun clearPlugin(pluginId: String) {
        val prefix = "$pluginId:"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    override fun getAll(pluginId: String): Map<String, String> {
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
        private const val PREFS_FILE_NAME = "latte_secure_plugin_prefs"

        private fun createEncryptedPrefs(context: Context): SharedPreferences {
            fun create(): SharedPreferences {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                return EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }

            return try {
                create()
            } catch (e: Exception) {
                try {
                    runCatching {
                        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                        keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                    }
                    create()
                } catch (e2: Exception) {
                    // Safe degradation on unrecoverable hardware Keystore corruption without deleting original data
                    context.getSharedPreferences("${PREFS_FILE_NAME}_fallback", Context.MODE_PRIVATE)
                }
            }
        }
    }
}
