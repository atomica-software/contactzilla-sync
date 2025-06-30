/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.provider.ContactsContract
import com.atomicasoftware.contactzillasync.db.Collection
import com.atomicasoftware.contactzillasync.db.Service
import com.atomicasoftware.contactzillasync.network.HttpClient
import com.atomicasoftware.contactzillasync.resource.LocalAddressBook
import com.atomicasoftware.contactzillasync.resource.LocalAddressBookStore
import com.atomicasoftware.contactzillasync.settings.AccountSettings
import com.atomicasoftware.contactzillasync.sync.account.setAndVerifyUserData
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.runBlocking
import java.util.logging.Level

/**
 * Sync logic for address books
 */
class AddressBookSyncer @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted resync: ResyncType?,
    @Assisted val syncFrameworkUpload: Boolean,
    @Assisted syncResult: SyncResult,
    addressBookStore: LocalAddressBookStore,
    private val accountSettingsFactory: AccountSettings.Factory,
    private val contactsSyncManagerFactory: ContactsSyncManager.Factory
): Syncer<LocalAddressBookStore, LocalAddressBook>(account, resync, syncResult) {

    @AssistedFactory
    interface Factory {
        fun create(
            account: Account,
            resyncType: ResyncType?,
            syncFrameworkUpload: Boolean,
            syncResult: SyncResult
        ): AddressBookSyncer
    }

    override val dataStore = addressBookStore

    override val serviceType: String
        get() = Service.TYPE_CARDDAV


    override fun getDbSyncCollections(serviceId: Long): List<Collection> =
        collectionRepository.getByServiceAndSync(serviceId)

    override fun syncCollection(provider: ContentProviderClient, localCollection: LocalAddressBook, remoteCollection: Collection) {
        logger.info("Synchronizing address book: ${localCollection.addressBookAccount.name}")
        syncAddressBook(
            account = account,
            addressBook = localCollection,
            httpClient = httpClient,
            provider = provider,
            syncResult = syncResult,
            collection = remoteCollection
        )
    }

    /**
     * Synchronizes an address book
     *
     * @param addressBook local address book
     * @param provider Content provider to access android contacts
     * @param syncResult Stores hard and soft sync errors
     * @param collection The database collection associated with this address book
     */
    private fun syncAddressBook(
        account: Account,
        addressBook: LocalAddressBook,
        httpClient: Lazy<HttpClient>,
        provider: ContentProviderClient,
        syncResult: SyncResult,
        collection: Collection
    ) {
        try {
            // handle group method change
            val accountSettings = accountSettingsFactory.create(account)
            val groupMethod = accountSettings.getGroupMethod().name

            val accountManager = AccountManager.get(context)
            accountManager.getUserData(addressBook.addressBookAccount, PREVIOUS_GROUP_METHOD)?.let { previousGroupMethod ->
                if (previousGroupMethod != groupMethod) {
                    logger.info("Group method changed, deleting all local contacts/groups")

                    // delete all local contacts and groups so that they will be downloaded again
                    provider.delete(addressBook.syncAdapterURI(ContactsContract.RawContacts.CONTENT_URI), null, null)
                    provider.delete(addressBook.syncAdapterURI(ContactsContract.Groups.CONTENT_URI), null, null)

                    // reset sync state
                    addressBook.syncState = null
                }
            }
            accountManager.setAndVerifyUserData(addressBook.addressBookAccount, PREVIOUS_GROUP_METHOD, groupMethod)

            val syncManager = contactsSyncManagerFactory.contactsSyncManager(
                account,
                httpClient.value,
                dataStore.authority,
                syncResult,
                provider,
                addressBook,
                collection,
                resync,
                syncFrameworkUpload
            )
            runBlocking {
                syncManager.performSync()
            }

        } catch(e: Exception) {
            logger.log(Level.SEVERE, "Couldn't sync contacts", e)
        }

        logger.info("Contacts sync complete")
    }


    companion object {

        const val PREVIOUS_GROUP_METHOD = "previous_group_method"

    }

}