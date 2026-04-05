package com.yshalsager.mafza.core.data.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import java.io.File

object ProfileDataStoreFactory {
    private const val DATASTORE_DIRECTORY = "datastore"
    private const val DATASTORE_FILENAME = "mafza_profile.enc.json"

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
        return DataStoreFactory.create(
            serializer = EncryptedProfileRecordSerializer,
            produceFile = {
                val datastore_dir = File(context.filesDir, DATASTORE_DIRECTORY)
                if (!datastore_dir.exists()) datastore_dir.mkdirs()
                File(datastore_dir, DATASTORE_FILENAME)
            }
        )
    }
}
