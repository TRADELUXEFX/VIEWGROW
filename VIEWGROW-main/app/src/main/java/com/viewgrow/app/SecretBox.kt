package com.viewgrow.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Encrypts the saved password with a key that lives in the Android Keystore (it never leaves the phone's secure
// hardware/keystore and is not included in any backup). The password is stored as "iv:ciphertext" in Base64.
object SecretBox {
    private const val ALIAS = "viewgrow_password_key"
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    // The key is looked up once and kept in memory: every Keystore call is slow on some phones (Samsung), and the
    // app checks the login many times when a screen opens. Repeating it on the main thread froze the screen white.
    @Volatile private var cached: SecretKey? = null

    @Synchronized
    private fun key(): SecretKey = cached ?: loadKey().also { cached = it }

    private fun loadKey(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    // Returns null if the Keystore could not be used (the caller then keeps nothing rather than plain text).
    fun encrypt(plain: String): String? = try {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = Base64.encodeToString(c.iv, Base64.NO_WRAP)
        val ct = Base64.encodeToString(c.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        "$iv:$ct"
    } catch (e: Exception) {
        null
    }

    // Returns "" if the value is missing, damaged, or the key is gone (the member simply logs in again).
    fun decrypt(stored: String): String = try {
        val parts = stored.split(":")
        if (parts.size != 2) "" else {
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(c.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        ""
    }
}
