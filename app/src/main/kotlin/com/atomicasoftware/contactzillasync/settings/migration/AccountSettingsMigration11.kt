/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.settings.migration

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import com.atomicasoftware.contactzillasync.settings.AccountSettings
import com.atomicasoftware.contactzillasync.settings.AccountSettings.Companion.SYNC_INTERVAL_MANUALLY
import com.atomicasoftware.contactzillasync.sync.account.setAndVerifyUserData
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Inject

/**
 * The tasks sync interval should be stored in account settings. It's used to set the sync interval
 * again when the tasks provider is switched.
 */
class AccountSettingsMigration11 @Inject constructor(
    @ApplicationContext private val context: Context,
): AccountSettingsMigration {

    override fun migrate(account: Account) {
        // nothing to do
    }

    private fun getSyncFrameworkInterval(account: Account, authority: String): Long? {
        if (ContentResolver.getIsSyncable(account, authority) <= 0)
            return null

        return if (ContentResolver.getSyncAutomatically(account, authority))
            ContentResolver.getPeriodicSyncs(account, authority).firstOrNull()?.period ?: SYNC_INTERVAL_MANUALLY
        else
            SYNC_INTERVAL_MANUALLY
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(11)
        abstract fun provide(impl: AccountSettingsMigration11): AccountSettingsMigration
    }

}