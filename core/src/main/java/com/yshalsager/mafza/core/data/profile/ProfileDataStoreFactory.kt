package com.yshalsager.mafza.core.data.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import java.io.File

object ProfileDataStoreFactory {
    private const val DATASTORE_DIRECTORY = "datastore"
    private const val DATASTORE_FILENAME = "mafza_profile.enc.json"
    private val data_store_lock = Any()
    @Volatile
    private var shared_data_store: DataStore<EncryptedProfileRecord>? = null

    fun create_profile_store(
        context: Context,
        profile_cipher: ProfileCipher = AndroidKeystoreProfileCipher()
    ): EncryptedProfileStore {
        return EncryptedProfileStore(
            data_store = create_data_store(context),
            profile_cipher = profile_cipher
        )
    }

    private fun create_data_store(context: Context): DataStore<EncryptedProfileRecord> {
        shared_data_store?.let { return it }

        return synchronized(data_store_lock) {
            shared_data_store?.let { return@synchronized it }

            val app_context = context.applicationContext
            val created = DataStoreFactory.create(
                serializer = EncryptedProfileRecordSerializer,
                produceFile = {
                    val datastore_dir = File(app_context.filesDir, DATASTORE_DIRECTORY)
                    if (!datastore_dir.exists()) datastore_dir.mkdirs()
                    File(datastore_dir, DATASTORE_FILENAME)
                }
            )
            shared_data_store = created
            created
        }
    }
}
