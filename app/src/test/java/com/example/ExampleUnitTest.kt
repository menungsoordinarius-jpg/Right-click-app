package com.example

import com.example.backend.BackendStatus
import com.example.backend.BackendType
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testBackendTypeEnum() {
        assertEquals(BackendType.SHIZUKU, BackendType.valueOf("SHIZUKU"))
        assertEquals(BackendType.ROOT, BackendType.valueOf("ROOT"))
    }

    @Test
    fun testBackendStatusEnum() {
        assertEquals(BackendStatus.READY, BackendStatus.valueOf("READY"))
        assertEquals(BackendStatus.PERMISSION_REQUIRED, BackendStatus.valueOf("PERMISSION_REQUIRED"))
        assertEquals(BackendStatus.NOT_RUNNING, BackendStatus.valueOf("NOT_RUNNING"))
        assertEquals(BackendStatus.DISCONNECTED, BackendStatus.valueOf("DISCONNECTED"))
    }
}
