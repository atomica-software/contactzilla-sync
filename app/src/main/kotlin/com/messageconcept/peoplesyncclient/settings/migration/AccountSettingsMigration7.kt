/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.settings.migration

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.provider.CalendarContract
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.sync.account.setAndVerifyUserData
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Inject
import kotlin.use

class AccountSettingsMigration7 @Inject constructor(
    @ApplicationContext private val context: Context
): AccountSettingsMigration {

    override fun migrate(account: Account) {
        // nothing to do for calendar colors

        // update allowed WiFi settings key
        val accountManager = AccountManager.get(context)
        val onlySSID = accountManager.getUserData(account, "wifi_only_ssid")
        accountManager.setAndVerifyUserData(account, AccountSettings.KEY_WIFI_ONLY_SSIDS, onlySSID)
        accountManager.setAndVerifyUserData(account, "wifi_only_ssid", null)
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(7)
        abstract fun provide(impl: AccountSettingsMigration7): AccountSettingsMigration
    }

}