package com.natam.gitflowmobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

/**
 * Stores the personal access token encrypted.
 * Self-healing: if the Keystore no longer matches what was saved
 * (reinstall, debug/release switch, Keystore reset), it wipes everything and recreates it.
 */
class TokenStore(private val context: Context) {

    private val prefsName = "gitflow_mobile_secure"
    private val tokenKey = "token"

    private var prefs: SharedPreferences = create()

    private fun build(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            prefsName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun create(): SharedPreferences {
        return try {
            build()
        } catch (e: Exception) {
            reset()
            build()
        }
    }

    private fun reset() {
        try {
            context.deleteSharedPreferences(prefsName)
        } catch (_: Exception) {
        }
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            ks.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        } catch (_: Exception) {
        }
    }

    fun getToken(): String {
        return try {
            prefs.getString(tokenKey, "").orEmpty()
        } catch (e: Exception) {
            reset()
            prefs = build()
            ""
        }
    }

    fun saveToken(token: String) {
        try {
            prefs.edit().putString(tokenKey, token).apply()
        } catch (e: Exception) {
            reset()
            prefs = build()
            prefs.edit().putString(tokenKey, token).apply()
        }
    }

    fun clear() {
        try {
            prefs.edit().remove(tokenKey).apply()
        } catch (e: Exception) {
            reset()
            prefs = build()
        }
    }
}
