package com.dieka.wallet.data

import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Versioned authenticated envelope: magic + version + 12-byte IV + ciphertext/tag. */
object VaultCipher {
    private val header = byteArrayOf(0x44, 0x4b, 0x56, 0x01)
    fun encrypt(plain: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(header)
        val encrypted = cipher.doFinal(plain)
        return ByteBuffer.allocate(header.size + 12 + encrypted.size)
            .put(header).put(cipher.iv).put(encrypted).array()
    }

    fun decrypt(envelope: ByteArray, key: SecretKey): ByteArray {
        require(envelope.size >= 32 && envelope.copyOfRange(0, 4).contentEquals(header)) {
            "Unsupported or damaged vault"
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope.copyOfRange(4, 16)))
        cipher.updateAAD(header)
        return cipher.doFinal(envelope, 16, envelope.size - 16)
    }
}
