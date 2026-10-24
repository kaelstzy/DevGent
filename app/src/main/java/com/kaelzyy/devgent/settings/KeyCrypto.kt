package com.kaelzyy.devgent.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeyCrypto {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "devgent_api_key"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + ciphertext
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > IV_BYTES) { "Payload kunci tidak valid." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            obtainKey(),
            GCMParameterSpec(GCM_TAG_BITS, payload, 0, IV_BYTES),
        )
        val plain = cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES)
        return String(plain, Charsets.UTF_8)
    }

    fun removeKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
    }

    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(ALIAS)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generator.generateKey()
        }
        val entry = keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Entri kunci tidak ditemukan.")
        return entry.secretKey
    }
}
