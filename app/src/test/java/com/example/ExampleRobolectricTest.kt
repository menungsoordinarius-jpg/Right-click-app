package com.example

import com.example.backend.BackendState
import com.example.backend.BackendStatus
import com.example.backend.BackendType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun `verify backend types and enum names`() {
        assertEquals("SHIZUKU", BackendType.SHIZUKU.name)
        assertEquals("ROOT", BackendType.ROOT.name)
    }

    @Test
    fun `verify initial backend state values`() {
        val state = BackendState(
            type = BackendType.SHIZUKU,
            status = BackendStatus.DISCONNECTED,
            message = "Belum terhubung",
            isReady = false
        )
        assertEquals(BackendType.SHIZUKU, state.type)
        assertEquals(BackendStatus.DISCONNECTED, state.status)
        assertFalse(state.isReady)
    }
}
