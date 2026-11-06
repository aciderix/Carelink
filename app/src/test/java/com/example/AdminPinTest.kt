package com.example

import com.example.security.AdminPin
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class AdminPinTest {
    @Test fun pinIsSaltedAndAnIncorrectCodeFails() {
        val a = AdminPin.hash("219384"); val b = AdminPin.hash("219384")
        assertNotEquals(a, b)
        assertTrue(AdminPin.verify("219384", a))
        assertFalse(AdminPin.verify("000000", a))
        assertFalse(AdminPin.verify("219384", "invalid"))
    }
}
