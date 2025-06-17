/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomica.contactzillasync.resource

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.Context
import com.atomica.contactzillasync.R
import at.bitfire.vcard4android.GroupMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import org.junit.Assert.assertTrue
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Provides [LocalTestAddressBook]s in tests.
 */
class LocalTestAddressBookProvider @Inject constructor(
    @ApplicationContext context: Context,
    private val localTestAddressBookFactory: LocalTestAddressBook.Factory
) {

    /**
     * Counter for creating unique address book names.
     */
    val counter = AtomicInteger()

    val accountManager = AccountManager.get(context)
    val accountType = context.getString(R.string.account_type_address_book)

    /**
     * Creates and provides a new temporary [LocalTestAddressBook] for the given [account] and
     * removes it again.
     *
     * @param account       The Contactzilla Sync account to use for the address book
     * @param provider      Content provider needed to access and modify the address book
     * @param groupMethod   The group method the address book should use
     * @param block         Function to execute with the temporary available address book
     */
    fun provide(
        account: Account,
        provider: ContentProviderClient,
        groupMethod: GroupMethod = GroupMethod.GROUP_VCARDS,
        block: (LocalTestAddressBook) -> Unit
    ) {
        // create new address book account
        val addressBookAccount = Account("Test Address Book ${counter.incrementAndGet()}", accountType)
        assertTrue(accountManager.addAccountExplicitly(addressBookAccount, null, null))
        val addressBook = localTestAddressBookFactory.create(account, addressBookAccount, provider, groupMethod)

        // Empty the address book (Needed by LocalGroupTest)
        for (contact in addressBook.queryContacts(null, null))
            contact.delete()
        for (group in addressBook.queryGroups(null, null))
            group.delete()

        try {
            // provide address book
            block(addressBook)
        } finally {
            // recreate account of provided address book, since the account might have been renamed
            val renamedAccount = Account(addressBook.addressBookAccount.name, addressBook.addressBookAccount.type)

            // remove address book account / address book
            assertTrue(accountManager.removeAccountExplicitly(renamedAccount))
        }
    }

}