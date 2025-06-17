/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomica.contactzillasync.settings.migration

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import androidx.test.rule.GrantPermissionRule
import com.atomica.contactzillasync.R
import com.atomica.contactzillasync.db.AppDatabase
import com.atomica.contactzillasync.db.Collection
import com.atomica.contactzillasync.db.Service
import com.atomica.contactzillasync.resource.LocalAddressBook
import com.atomica.contactzillasync.sync.account.TestAccount
import com.atomica.contactzillasync.sync.account.setAndVerifyUserData
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AccountSettingsMigration17Test {

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var migration: AccountSettingsMigration17

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val permissionRule = GrantPermissionRule.grant(android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.WRITE_CONTACTS)


    @Before
    fun setUp() {
        hiltRule.inject()
    }


    @Test
    fun testMigrate_OldAddressBook_CollectionInDB() {
        val localAddressBookUserDataUrl = "url"
        TestAccount.provide(version = 16) { account ->
            val accountManager = AccountManager.get(context)
            val addressBookAccountType = context.getString(R.string.account_type_address_book)
            var addressBookAccount = Account("Address Book", addressBookAccountType)
            assertTrue(accountManager.addAccountExplicitly(addressBookAccount, null, null))

            try {
                // address book has account + URL
                val url = "https://example.com/address-book"
                accountManager.setAndVerifyUserData(addressBookAccount, "real_account_name", account.name)
                accountManager.setAndVerifyUserData(addressBookAccount, localAddressBookUserDataUrl, url)

                // and is known in database
                db.serviceDao().insertOrReplace(
                    Service(
                        id = 1, accountName = account.name, type = Service.TYPE_CARDDAV, principal = null
                    )
                )
                db.collectionDao().insert(
                    Collection(
                        id = 100,
                        serviceId = 1,
                        url = url.toHttpUrl(),
                        type = Collection.TYPE_ADDRESSBOOK,
                        displayName = "Some Address Book"
                    )
                )

                // run migration
                migration.migrate(account)

                // migration renames address book, update account
                addressBookAccount = accountManager.getAccountsByType(addressBookAccountType).filter {
                    accountManager.getUserData(it, localAddressBookUserDataUrl) == url
                }.first()
                assertEquals("Some Address Book (${account.name}) #100", addressBookAccount.name)

                // ID is now assigned
                assertEquals(100L, accountManager.getUserData(addressBookAccount, LocalAddressBook.USER_DATA_COLLECTION_ID)?.toLong())
            } finally {
                accountManager.removeAccountExplicitly(addressBookAccount)
            }
        }
    }

}