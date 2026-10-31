/* Copyright 2026 Shiguang Transfer contributors. Licensed under Apache-2.0. */
package com.lumalink.g100d.transport.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

data class SavedCamera(val ssid: String, val password: String, val baseUrl: String, val autoConnect: Boolean = true)

/** Local-only credentials: encrypt with an Android Keystore key; never include in backups or logs. */
class CameraProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("shiguang_camera_profile", Context.MODE_PRIVATE)
    private val alias = "shiguang.camera.profile.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    fun read(): SavedCamera? = runCatching {
        val encoded = prefs.getString("ciphertext", null) ?: return null
        val iv = Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val json = JSONObject(String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), Charsets.UTF_8))
        SavedCamera(json.getString("ssid"), json.getString("password"), json.getString("baseUrl"), json.optBoolean("auto", true))
    }.getOrNull()
    fun save(profile: SavedCamera) {
        val data = JSONObject().put("ssid", profile.ssid).put("password", profile.password)
            .put("baseUrl", profile.baseUrl).put("auto", profile.autoConnect).toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("ciphertext", Base64.encodeToString(cipher.doFinal(data), Base64.NO_WRAP)).apply()
    }
    fun forget() { prefs.edit().clear().apply() }
}
