/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import com.atomicasoftware.contactzillasync.repository.DavCollectionRepository
import com.atomicasoftware.contactzillasync.repository.DavServiceRepository
import com.atomicasoftware.contactzillasync.settings.AccountSettings
import com.atomicasoftware.contactzillasync.sync.SyncFrameworkIntegration
import at.bitfire.vcard4android.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Optional
import java.util.logging.Logger

/**
 * A local address book that provides an easy way to set the group method in tests.
 */
class LocalTestAddressBook @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted("addressBook") addressBookAccount: Account,
    @Assisted provider: ContentProviderClient,
    @Assisted override val groupMethod: GroupMethod,
    accountSettingsFactory: AccountSettings.Factory,
    collectionRepository: DavCollectionRepository,
    @ApplicationContext context: Context,
    logger: Logger,
    serviceRepository: DavServiceRepository,
    syncFramework: SyncFrameworkIntegration
): LocalAddressBook(
    account = account,
    _addressBookAccount = addressBookAccount,
    provider = provider,
    accountSettingsFactory = accountSettingsFactory,
    collectionRepository = collectionRepository,
    context = context,
    dirtyVerifier = Optional.empty(),
    logger = logger,
    serviceRepository = serviceRepository,
    syncFramework = syncFramework
) {

    @AssistedFactory
    interface Factory {
        fun create(
            account: Account,
            @Assisted("addressBook") addressBookAccount: Account,
            provider: ContentProviderClient,
            groupMethod: GroupMethod
        ): LocalTestAddressBook
    }

}