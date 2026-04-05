package com.yshalsager.mafza.core.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunHistoryRetentionPolicyTest {
    @Test
    fun `returns empty prune list when size within retention`() {
        val run_ids = (1..HISTORY_RETENTION_COUNT).map { "run_$it" }

        val run_ids_to_prune = RunHistoryRetentionPolicy.run_ids_to_prune(run_ids, keep_latest = HISTORY_RETENTION_COUNT)

        assertEquals(emptyList<String>(), run_ids_to_prune)
    }

    @Test
    fun `returns oldest ids when size exceeds retention`() {
        val run_ids = (1..105).map { "run_$it" }

        val run_ids_to_prune = RunHistoryRetentionPolicy.run_ids_to_prune(run_ids, keep_latest = 100)

        assertEquals((101..105).map { "run_$it" }, run_ids_to_prune)
    }

    @Test
    fun `stderr snippet is truncated to max length`() {
        val long_snippet = buildString {
            repeat(MAX_STDERR_SNIPPET_LENGTH + 10) { append('x') }
        }

        val sanitized = RunHistoryRetentionPolicy.sanitize_stderr_snippet(long_snippet)

        assertEquals(MAX_STDERR_SNIPPET_LENGTH, sanitized?.length)
    }

    @Test
    fun `null stderr snippet stays null`() {
        val sanitized = RunHistoryRetentionPolicy.sanitize_stderr_snippet(null)
        assertNull(sanitized)
    }
}
