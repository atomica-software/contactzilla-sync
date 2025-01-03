/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import at.bitfire.davdroid.db.Collection

/**
 * Represents a local data store for a specific collection type.
 * Manages creation, update, and deletion of collections of the given type.
 */
interface LocalDataStore<T: LocalCollection<*>> {

    /**
     * Creates a new local collection from the given (remote) collection info.
     *
     * @param provider       the content provider client
     * @param fromCollection collection info
     *
     * @return the new local collection, or `null` if creation failed
     */
    fun create(provider: ContentProviderClient, fromCollection: Collection): T?

    /**
     * Returns all local collections of the data store, including those which don't have a corresponding remote
     * [Collection] entry.
     *
     * @param account  the account that the data store is associated with
     * @param provider the content provider client
     *
     * @return a list of all local collections
     */
    fun getAll(account: Account, provider: ContentProviderClient): List<T>

    /**
     * Updates the local collection with the data from the given (remote) collection info.
     *
     * @param provider        the content provider client
     * @param localCollection the local collection to update
     * @param fromCollection  collection info
     */
    fun update(provider: ContentProviderClient, localCollection: T, fromCollection: Collection)

    /**
     * Deletes the local collection.
     *
     * @param localCollection the local collection to delete
     */
    fun delete(localCollection: T)

    /**
     * Changes the account assigned to the containing data to another one.
     *
     * @param oldAccount The old account.
     * @param newAccount The new account.
     */
    fun updateAccount(oldAccount: Account, newAccount: Account)

}