package com.yshalsager.mafza.emergency.steps

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.yshalsager.mafza.core.contracts.DeleteTarget
import com.yshalsager.mafza.emergency.shell.PrivilegedCommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ContentUriDeleteExecutor {
    suspend fun execute_delete(target: DeleteTarget, timeout_seconds: Int): PrivilegedCommandResult
}

object UnavailableContentUriDeleteExecutor : ContentUriDeleteExecutor {
    override suspend fun execute_delete(target: DeleteTarget, timeout_seconds: Int): PrivilegedCommandResult {
        return PrivilegedCommandResult(
            exit_code = 1,
            stdout = "",
            stderr = "content_uri_executor_unavailable",
            timed_out = false,
            unavailable = true
        )
    }
}

class AndroidContentUriDeleteExecutor(
    private val app_context: Context
) : ContentUriDeleteExecutor {
    override suspend fun execute_delete(target: DeleteTarget, timeout_seconds: Int): PrivilegedCommandResult {
        return withContext(Dispatchers.IO) {
            execute_delete_blocking(target)
        }
    }

    private fun execute_delete_blocking(target: DeleteTarget): PrivilegedCommandResult {
        val content_uri_value = target.content_uri?.trim().orEmpty()
        if (content_uri_value.isEmpty()) {
            return failed_result("empty_content_uri")
        }

        val parsed_uri = runCatching { Uri.parse(content_uri_value) }.getOrNull()
            ?: return failed_result("invalid_content_uri")
        if (!parsed_uri.scheme.equals("content", ignoreCase = true)) {
            return failed_result("unsupported_content_uri_scheme")
        }

        val resolved_target = resolve_document_target(parsed_uri) ?: return failed_result("unsupported_content_uri")
        return try {
            val deleted = if (target.recursive) {
                delete_recursive(resolved_target)
            } else {
                delete_document(resolved_target.document_uri)
            }
            if (deleted) {
                successful_result()
            } else {
                failed_result("delete_returned_false")
            }
        } catch (_: SecurityException) {
            unavailable_result("content_uri_permission_denied")
        } catch (throwable: Throwable) {
            failed_result(throwable.message ?: "content_uri_delete_failed")
        }
    }

    private fun resolve_document_target(uri: Uri): ResolvedDocumentTarget? {
        if (DocumentsContract.isTreeUri(uri)) {
            val tree_document_id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
            val document_uri = DocumentsContract.buildDocumentUriUsingTree(uri, tree_document_id)
            return ResolvedDocumentTarget(document_uri = document_uri, tree_context_uri = uri)
        }
        if (DocumentsContract.isDocumentUri(app_context, uri)) {
            return ResolvedDocumentTarget(document_uri = uri, tree_context_uri = null)
        }
        return null
    }

    private fun delete_recursive(target: ResolvedDocumentTarget): Boolean {
        if (is_directory(target.document_uri)) {
            val child_targets = list_child_targets(target) ?: return false
            child_targets.forEach { child_target ->
                if (!delete_recursive(child_target)) return false
            }
        }
        return delete_document(target.document_uri)
    }

    private fun list_child_targets(parent: ResolvedDocumentTarget): List<ResolvedDocumentTarget>? {
        val resolver = app_context.contentResolver
        val parent_document_id = runCatching { DocumentsContract.getDocumentId(parent.document_uri) }.getOrNull() ?: return null
        val authority = parent.document_uri.authority ?: return null
        val children_uri = if (parent.tree_context_uri != null) {
            DocumentsContract.buildChildDocumentsUriUsingTree(parent.tree_context_uri, parent_document_id)
        } else {
            DocumentsContract.buildChildDocumentsUri(authority, parent_document_id)
        }

        return runCatching {
            resolver.query(
                children_uri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val child_document_id = cursor.getString(0) ?: continue
                        val child_document_uri = if (parent.tree_context_uri != null) {
                            DocumentsContract.buildDocumentUriUsingTree(parent.tree_context_uri, child_document_id)
                        } else {
                            DocumentsContract.buildDocumentUri(authority, child_document_id)
                        }
                        add(
                            ResolvedDocumentTarget(
                                document_uri = child_document_uri,
                                tree_context_uri = parent.tree_context_uri
                            )
                        )
                    }
                }
            } ?: emptyList()
        }.getOrNull()
    }

    private fun is_directory(document_uri: Uri): Boolean {
        val resolver = app_context.contentResolver
        return runCatching {
            resolver.query(
                document_uri,
                arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use false
                val mime_type = cursor.getString(0)
                mime_type == DocumentsContract.Document.MIME_TYPE_DIR
            } ?: false
        }.getOrDefault(false)
    }

    private fun delete_document(document_uri: Uri): Boolean {
        return runCatching {
            DocumentsContract.deleteDocument(app_context.contentResolver, document_uri)
        }.getOrDefault(false)
    }

    private fun successful_result(): PrivilegedCommandResult {
        return PrivilegedCommandResult(
            exit_code = 0,
            stdout = "",
            stderr = "",
            timed_out = false,
            unavailable = false
        )
    }

    private fun failed_result(message: String): PrivilegedCommandResult {
        return PrivilegedCommandResult(
            exit_code = 1,
            stdout = "",
            stderr = message,
            timed_out = false,
            unavailable = false
        )
    }

    private fun unavailable_result(message: String): PrivilegedCommandResult {
        return PrivilegedCommandResult(
            exit_code = 1,
            stdout = "",
            stderr = message,
            timed_out = false,
            unavailable = true
        )
    }

    private data class ResolvedDocumentTarget(
        val document_uri: Uri,
        val tree_context_uri: Uri?
    )
}
