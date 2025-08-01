/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.settings.migration

import android.accounts.Account
import android.content.Context
import android.provider.CalendarContract
import androidx.work.WorkManager
import com.atomicasoftware.contactzillasync.sync.AutomaticSyncManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Inject

/**
 * Sync workers are now not per authority anymore, but per [com.atomicasoftware.contactzillasync.sync.SyncDataType]. So we have to
 *
 * 1. cancel all current periodic sync workers (which have "authority" input data),
 * 2. re-enqueue periodic sync workers (now with "data type" input data), if applicable.
 */
class AccountSettingsMigration19 @Inject constructor(
    @ApplicationContext private val context: Context,
    private val automaticSyncManager: AutomaticSyncManager
): AccountSettingsMigration {

    override fun migrate(account: Account) {
        // cancel old workers
        val workManager = WorkManager.getInstance(context)
        val authorities = listOf(
            "com.atomicasoftware.contactzillasync.addressbooks",
        )
        for (authority in authorities) {
            val oldWorkerName = "periodic-sync $authority ${account.type}/${account.name}"
            workManager.cancelUniqueWork(oldWorkerName)
        }

        // enqueue new workers
        automaticSyncManager.updateAutomaticSync(account)
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(19)
        abstract fun provide(impl: AccountSettingsMigration19): AccountSettingsMigration
    }

}