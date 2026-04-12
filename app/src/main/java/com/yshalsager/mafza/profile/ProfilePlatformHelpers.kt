package com.yshalsager.mafza.profile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract

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

internal fun query_message_share_apps(context: Context): List<LaunchableAppOption> {
    val package_manager = context.packageManager
    val share_intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
    }
    return package_manager
        .queryIntentActivities(share_intent, PackageManager.MATCH_ALL)
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
