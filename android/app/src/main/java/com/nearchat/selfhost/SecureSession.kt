package com.nearchat.selfhost

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypt bearer token with Android Keystore AES-GCM; no unencrypted token in preferences. */
internal class SecureSession(context: Context) {
    private val prefs = context.getSharedPreferences("near_chat_server_session", Context.MODE_PRIVATE)
    private val alias = "near_chat_selfhost_session_aes"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val old = store.getKey(alias, null) as? SecretKey
        if (old != null) return old
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
    fun save(url: String, username: String, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        prefs.edit().putString("url", url).putString("username", username)
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("ciphertext", Base64.encodeToString(cipher.doFinal(token.toByteArray()), Base64.NO_WRAP)).apply()
    }
    fun token(): String? = try {
        val iv = Base64.decode(prefs.getString("iv", "") ?: "", Base64.NO_WRAP)
        val ciphertext = Base64.decode(prefs.getString("ciphertext", "") ?: "", Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    } catch (_: Exception) { null }
    fun url(): String = prefs.getString("url", "") ?: ""
    fun username(): String = prefs.getString("username", "") ?: ""
    fun clear() { prefs.edit().clear().apply() }
}
