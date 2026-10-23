package com.dieka.wallet

import com.dieka.wallet.data.VaultCipher
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.AEADBadTagException

class VaultCipherTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun roundTripKeepsAllCardFields() {
        val key = key()
        val plain = """{"bank":"招商银行","number":"4111111111111111","expiry":"12/29","cvv":"123"}""".toByteArray()
        val encrypted = VaultCipher.encrypt(plain, key)
        assertArrayEquals(plain, VaultCipher.decrypt(encrypted, key))
        assertFalse(String(encrypted).contains("4111111111111111"))
    }

    @Test fun everySaveHasFreshIv() {
        val key = key()
        val data = "same card".toByteArray()
        assertFalse(VaultCipher.encrypt(data, key).contentEquals(VaultCipher.encrypt(data, key)))
    }

    @Test(expected = AEADBadTagException::class)
    fun tamperedVaultFailsClosed() {
        val key = key()
        val data = VaultCipher.encrypt("card data".toByteArray(), key)
        data[data.lastIndex] = (data.last().toInt() xor 1).toByte()
        VaultCipher.decrypt(data, key)
    }

    @Test(expected = AEADBadTagException::class)
    fun wrongKeyCannotReadVault() {
        VaultCipher.decrypt(VaultCipher.encrypt("card".toByteArray(), key()), key())
    }

    @Test(expected = IllegalArgumentException::class)
    fun truncatedEnvelopeIsRejected() { VaultCipher.decrypt(byteArrayOf(1, 2, 3), key()) }
}
