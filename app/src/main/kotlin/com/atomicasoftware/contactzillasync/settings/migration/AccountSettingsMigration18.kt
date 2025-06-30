/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.settings.migration

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import com.atomicasoftware.contactzillasync.R
import com.atomicasoftware.contactzillasync.db.AppDatabase
import com.atomicasoftware.contactzillasync.db.Service
import com.atomicasoftware.contactzillasync.resource.LocalAddressBook
import com.atomicasoftware.contactzillasync.sync.account.setAndVerifyUserData
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * v17 had removed the binding between address book accounts and accounts and introduced
 * the binding to collection IDs instead.
 *
 * However, it turned out that the account binding is needed even with collection IDs for the case
 * that the collection is not available in the database anymore (for instance, because it has been
 * removed on the server). In that case, the [com.atomicasoftware.contactzillasync.sync.Syncer] still needs to get
 * a list of all address book accounts that belong to the account, and not _all_ address books.
 *
 * So this migration again assigns address book accounts to accounts.
 */
class AccountSettingsMigration18 @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase
): AccountSettingsMigration {

    override fun migrate(account: Account) {
        val accountManager = AccountManager.get(context)
        runBlocking {
            db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CARDDAV)?.let { service ->
                db.collectionDao().getByService(service.id).forEach { collection ->
                    // Find associated address book account by collection ID (if it exists)
                    val addressBookAccount = accountManager
                        .getAccountsByType(context.getString(R.string.account_type_address_book))
                        .firstOrNull {
                            accountManager.getUserData(
                                it,
                                LocalAddressBook.USER_DATA_COLLECTION_ID
                            ) == collection.id.toString()
                        }

                    if (addressBookAccount != null) {
                        // (Re-)assign address book to account
                        accountManager.setAndVerifyUserData(addressBookAccount, LocalAddressBook.USER_DATA_ACCOUNT_NAME, account.name)
                        accountManager.setAndVerifyUserData(addressBookAccount, LocalAddressBook.USER_DATA_ACCOUNT_TYPE, account.type)
                    }
                }
            }
        }

        // Address books without an assigned account will be removed by AccountsCleanupWorker
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(18)
        abstract fun provide(impl: AccountSettingsMigration18): AccountSettingsMigration
    }

}