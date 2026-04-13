package com.sopsie.util

import com.sopsie.model.SopsError
import com.sopsie.model.SopsException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Notification surface (showError/showInfo) is platform UI and is exercised
 * via platform tests. These cover the pure exception-mapping logic.
 */
class ErrorHandlerTest {

    @Test fun `SopsException unwraps to its inner error message`() {
        val ex = SopsException(SopsError.timeout(5000))
        val msg = ErrorHandler.getUserFriendlyMessage(ex)
        assertTrue(msg.contains("5000"))
    }

    @Test fun `IOException is prefixed with "File operation failed"`() {
        val msg = ErrorHandler.getUserFriendlyMessage(IOException("disk full"))
        assertTrue(msg.startsWith("File operation failed:"))
        assertTrue(msg.contains("disk full"))
    }

    @Test fun `SecurityException is prefixed with "Permission denied"`() {
        val msg = ErrorHandler.getUserFriendlyMessage(SecurityException("EACCES"))
        assertTrue(msg.startsWith("Permission denied:"))
    }

    @Test fun `InterruptedException maps to a cancellation message`() {
        val msg = ErrorHandler.getUserFriendlyMessage(InterruptedException())
        assertEquals("Operation was cancelled", msg)
    }

    @Test fun `unknown exception falls back to its message`() {
        val msg = ErrorHandler.getUserFriendlyMessage(IllegalStateException("nope"))
        assertEquals("nope", msg)
    }

    @Test fun `unknown exception with null message falls back to a generic message`() {
        val msg = ErrorHandler.getUserFriendlyMessage(RuntimeException())
        assertNotNull(msg)
        assertTrue(msg.isNotEmpty())
    }
}
