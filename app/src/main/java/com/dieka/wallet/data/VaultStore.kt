package com.dieka.wallet.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class VaultStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "cards.vault"))
    private val alias = "dieka.vault.aes.v1"

    fun exists(): Boolean = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()

    fun prepareKey() {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(alias)) return
        // Never generate a replacement key over an existing, unreadable vault.
        check(!exists()) { "Vault key unavailable" }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationParameters(
                        300,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    )
                    .build()
            )
            generateKey()
        }
    }

    private fun key(): SecretKey = KeyStore.getInstance("AndroidKeyStore").run {
        load(null)
        getKey(alias, null) as? SecretKey ?: error("Vault key unavailable")
    }

    @Synchronized fun load(): VaultSnapshot {
        if (!exists()) return VaultSnapshot()
        val plain = VaultCipher.decrypt(file.readFully(), key())
        try {
            return VaultCodec.decode(String(plain, Charsets.UTF_8))
        } finally { plain.fill(0) }
    }

    @Synchronized fun save(snapshot: VaultSnapshot) {
        val plain = VaultCodec.encode(snapshot).toByteArray(Charsets.UTF_8)
        val encrypted = try { VaultCipher.encrypt(plain, key()) } finally { plain.fill(0) }
        val output = file.startWrite()
        try {
            output.write(encrypted)
            file.finishWrite(output)
        } catch (e: Exception) {
            file.failWrite(output)
            throw e
        }
    }
}
