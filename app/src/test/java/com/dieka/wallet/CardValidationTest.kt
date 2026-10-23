package com.dieka.wallet

import com.dieka.wallet.data.CardValidation
import org.junit.Assert.*
import org.junit.Test

class CardValidationTest {
    @Test fun cardNumberBoundaries() {
        assertNull(CardValidation.numberError("123456789012"))
        assertNull(CardValidation.numberError("1234567890123456789"))
        assertNotNull(CardValidation.numberError("123"))
        assertNotNull(CardValidation.numberError("12345678901234567890"))
        assertNotNull(CardValidation.numberError("411111111111abcd"))
    }
    @Test fun expiryChecksMonthAndFormat() {
        assertNull(CardValidation.expiryError("01/29", false))
        assertNull(CardValidation.expiryError("12/29", false))
        listOf("00/29", "13/29", "1/29", "12/2029", "").forEach { assertNotNull(CardValidation.expiryError(it, false)) }
        assertNull(CardValidation.expiryError("", true))
    }
    @Test fun securityCodeIsOptionalButValidated() {
        listOf("", "123", "1234").forEach { assertNull(CardValidation.cvvError(it)) }
        listOf("12", "12345", "abc").forEach { assertNotNull(CardValidation.cvvError(it)) }
    }
}
