package com.reminder.local.diagnostics.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DiagnosticTraceIdTest {
    @Test
    fun `same alert instance has stable identity without user content`() {
        assertEquals(
            DiagnosticTraceId.alert(1L, 2, "DUE", 3L),
            DiagnosticTraceId.alert(1L, 2, "DUE", 3L)
        )
        assertNotEquals(
            DiagnosticTraceId.alert(1L, 2, "DUE", 3L),
            DiagnosticTraceId.alert(1L, 2, "ADVANCE", 3L)
        )
    }
}
