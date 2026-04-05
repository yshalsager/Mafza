package com.yshalsager.mafza.core.data.history

const val HISTORY_RETENTION_COUNT = 100
const val MAX_STDERR_SNIPPET_LENGTH = 512

object RunHistoryRetentionPolicy {
    fun run_ids_to_prune(run_ids_sorted_desc: List<String>, keep_latest: Int = HISTORY_RETENTION_COUNT): List<String> {
        if (keep_latest <= 0) return run_ids_sorted_desc
        if (run_ids_sorted_desc.size <= keep_latest) return emptyList()
        return run_ids_sorted_desc.drop(keep_latest)
    }

    fun sanitize_stderr_snippet(stderr_snippet: String?): String? {
        return stderr_snippet?.take(MAX_STDERR_SNIPPET_LENGTH)
    }
}
