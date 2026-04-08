package com.yshalsager.mafza.profile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.DocumentsContract

internal fun query_launchable_apps(context: Context): List<LaunchableAppOption> {
    val package_manager = context.packageManager
    val launcher_intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    return package_manager
        .queryIntentActivities(launcher_intent, PackageManager.MATCH_ALL)
        .map { resolve_info ->
            val activity_info = resolve_info.activityInfo
            val label = resolve_info.loadLabel(package_manager).toString()
            LaunchableAppOption(
                label = label,
                package_name = activity_info.packageName,
                activity_name = activity_info.name
            )
        }
        .distinctBy { it.package_name to it.activity_name }
        .sortedBy { it.label.lowercase() }
}

internal fun query_installed_app_packages(context: Context): List<InstalledPackageOption> {
    val package_manager = context.packageManager
    val launcher_intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    return package_manager
        .queryIntentActivities(launcher_intent, PackageManager.MATCH_ALL)
        .map { resolve_info ->
            val activity_info = resolve_info.activityInfo
            val label = resolve_info.loadLabel(package_manager).toString()
            InstalledPackageOption(
                label = label,
                package_name = activity_info.packageName
            )
        }
        .distinctBy { it.package_name }
        .sortedBy { it.label.lowercase() }
}

internal fun resolve_picker_uri_to_absolute_path(
    context: Context,
    selected_uri: Uri,
    prefer_tree_id: Boolean
): String? {
    if (selected_uri.scheme == "file") {
        return selected_uri.path?.trim()?.ifEmpty { null }
    }
    if (!DocumentsContract.isDocumentUri(context, selected_uri)) return null

    val document_id = runCatching {
        if (prefer_tree_id && DocumentsContract.isTreeUri(selected_uri)) {
            DocumentsContract.getTreeDocumentId(selected_uri)
        } else {
            DocumentsContract.getDocumentId(selected_uri)
        }
    }.getOrNull() ?: return null

    return document_id_to_absolute_path(document_id)
}

internal fun document_id_to_absolute_path(document_id: String): String? {
    if (document_id.startsWith("raw:")) {
        return document_id.removePrefix("raw:").trim().ifEmpty { null }
    }

    if (document_id.startsWith("/")) {
        return document_id.trim().ifEmpty { null }
    }

    val id_parts = document_id.split(':', limit = 2)
    if (id_parts.isEmpty()) return null

    val storage_id = id_parts[0]
    val relative_path = id_parts.getOrNull(1).orEmpty().trim('/')
    return when {
        storage_id.equals("primary", ignoreCase = true) -> {
            if (relative_path.isEmpty()) "/storage/emulated/0" else "/storage/emulated/0/$relative_path"
        }
        storage_id.equals("home", ignoreCase = true) -> {
            if (relative_path.isEmpty()) "/storage/emulated/0/Documents" else "/storage/emulated/0/Documents/$relative_path"
        }
        relative_path.isNotEmpty() -> "/storage/$storage_id/$relative_path"
        else -> null
    }
}

internal fun resolve_contact_phone_number(
    context: Context,
    contact_uri: Uri
): String? {
    return runCatching {
        val contact_id = context.contentResolver.query(
            contact_uri,
            arrayOf(ContactsContract.Contacts._ID),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getString(0)
        } ?: return@runCatching null

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contact_id),
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getString(0)
        }?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}

internal fun move_string_item(
    values: List<String>,
    from_index: Int,
    to_index: Int
): List<String> {
    if (from_index !in values.indices || to_index !in values.indices) return values
    val mutable_values = values.toMutableList()
    val moving_value = mutable_values.removeAt(from_index)
    mutable_values.add(to_index, moving_value)
    return mutable_values
}

internal fun <T> move_item(
    values: List<T>,
    from_index: Int,
    to_index: Int
): List<T> {
    if (from_index !in values.indices || to_index !in values.indices) return values
    val mutable_values = values.toMutableList()
    val moving_value = mutable_values.removeAt(from_index)
    mutable_values.add(to_index, moving_value)
    return mutable_values
}
