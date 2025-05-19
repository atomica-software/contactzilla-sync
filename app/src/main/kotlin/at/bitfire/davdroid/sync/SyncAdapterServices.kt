/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import android.os.IBinder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.LocalAddressBook.Companion.USER_DATA_COLLECTION_ID
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.davdroid.sync.worker.BaseSyncWorker
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

abstract class SyncAdapterService: Service() {

    /**
     * We don't use @AndroidEntryPoint / @Inject because it's unavoidable that instrumented tests sometimes accidentally / asynchronously
     * create a [SyncAdapterService] instance before Hilt is initialized during the tests.
     */
    @dagger.hilt.EntryPoint
    @InstallIn(SingletonComponent::class)
    interface EntryPoint {
        fun syncAdapter(): SyncAdapter
    }

    override fun onBind(intent: Intent?): IBinder {
        if (BuildConfig.DEBUG && !syncActive.get()) {
            // only for debug builds/testing: syncActive flag
            val logger = Logger.getLogger(this@SyncAdapterService::class.java.name)
            logger.log(Level.WARNING, "SyncAdapterService.onBind() was called but syncActive = false. Ignoring")

            val fakeAdapter = object: AbstractThreadedSyncAdapter(this, false) {
                override fun onPerformSync(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
                    val message = StringBuilder()
                    message.append("FakeSyncAdapter onPerformSync(account=$account, extras=$extras, authority=$authority, syncResult=$syncResult)")
                    for (key in extras.keySet())
                        message.append("\n\textras[$key] = ${extras[key]}")
                    logger.warning(message.toString())
                }
            }
            return fakeAdapter.syncAdapterBinder
        }

        // create sync adapter via Hilt
        val entryPoint = EntryPointAccessors.fromApplication<EntryPoint>(this)
        val syncAdapter = entryPoint.syncAdapter()
        return syncAdapter.syncAdapterBinder
    }

    companion object {
        /**
         * Flag to indicate whether the sync adapter should be active. When it is `false`, synchronization will not be run
         * (only intended for tests).
         */
        val syncActive = AtomicBoolean(true)
    }


    /**
     * Entry point for the Sync Adapter Framework.
     *
     * Handles incoming sync requests from the Sync Adapter Framework.
     *
     * Although we do not use the sync adapter for syncing anymore, we keep this sole
     * adapter to provide exported services, which allow android system components and calendar,
     * contacts or task apps to sync via DAVx5.
     *
     * All Sync Adapter Framework related interaction should happen inside [SyncFrameworkIntegration].
     */
    class SyncAdapter @Inject constructor(
        private val accountSettingsFactory: AccountSettings.Factory,
        private val collectionRepository: DavCollectionRepository,
        private val serviceRepository: DavServiceRepository,
        @ApplicationContext context: Context,
        private val logger: Logger,
        private val syncConditionsFactory: SyncConditions.Factory,
        private val syncWorkerManager: SyncWorkerManager
    ): AbstractThreadedSyncAdapter(
        /* context = */ context,
        /* autoInitialize = */ true     // Sets isSyncable=1 when isSyncable=-1 and SYNC_EXTRAS_INITIALIZE is set.
                                        // Doesn't matter for us because we have android:isAlwaysSyncable="true" for all sync adapters.
    ) {

        /**
         * Scope used to wait until the synchronization is finished. Will be cancelled when the sync framework
         * requests cancellation.
         */
        private val waitScope = CoroutineScope(Dispatchers.Default)

        override fun onPerformSync(accountOrAddressBookAccount: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            // We have to pass this old SyncFramework extra for an Android 7 workaround
            val upload = extras.containsKey(ContentResolver.SYNC_EXTRAS_UPLOAD)
            logger.info("Sync request via sync framework for $accountOrAddressBookAccount $authority (upload=$upload)")

            // If we should sync an address book account - find the account storing the settings
            val account = if (accountOrAddressBookAccount.type == context.getString(R.string.account_type_address_book))
                AccountManager.get(context)
                    .getUserData(accountOrAddressBookAccount, USER_DATA_COLLECTION_ID)
                    ?.toLongOrNull()
                    ?.let { collectionId ->
                    collectionRepository.get(collectionId)?.let { collection ->
                        serviceRepository.getBlocking(collection.serviceId)?.let { service ->
                            Account(service.accountName, context.getString(R.string.account_type))
                        }
                    }
                }
            else
                accountOrAddressBookAccount

            if (account == null) {
                logger.warning("Address book account $accountOrAddressBookAccount doesn't have an associated collection")
                return
            }

            val accountSettings = try {
                accountSettingsFactory.create(account)
            } catch (e: InvalidAccountException) {
                logger.log(Level.WARNING, "Account doesn't exist anymore", e)
                return
            }

            val syncConditions = syncConditionsFactory.create(accountSettings)
            // Should we run the sync at all?
            if (!syncConditions.wifiConditionsMet()) {
                logger.info("Sync conditions not met. Aborting sync framework initiated sync")
                return
            }

            logger.fine("Starting OneTimeSyncWorker for $account $authority and waiting for it")
            val workerName = syncWorkerManager.enqueueOneTime(account, dataType = SyncDataType.fromAuthority(authority), syncFrameworkUpload = upload)

            /* Because we are not allowed to observe worker state on a background thread, we can not
            use it to block the sync adapter. Instead we use a Flow to get notified when the sync
            has finished. */
            val workManager = WorkManager.getInstance(context)

            try {
                val waitJob = waitScope.launch {
                    // wait for finished worker state
                    workManager.getWorkInfosForUniqueWorkFlow(workerName).collect { infoList ->
                        for (info in infoList)
                            if (info.state.isFinished) {
                                if (info.state == WorkInfo.State.FAILED) {
                                    if (info.outputData.getBoolean(BaseSyncWorker.OUTPUT_TOO_MANY_RETRIES, false))
                                        syncResult.tooManyRetries = true
                                    else
                                        syncResult.databaseError = true
                                }
                                cancel("$workerName has finished")
                            }
                    }
                }

                runBlocking {
                    withTimeout(10 * 60 * 1000) {   // block max. 10 minutes
                        waitJob.join()              // wait until worker has finished
                    }
                }
            } catch (_: CancellationException) {
                // waiting for work was cancelled, either by timeout or because the worker has finished
                logger.fine("Not waiting for OneTimeSyncWorker anymore.")
            }

            logger.log(Level.INFO, "Returning to sync framework.", syncResult)
        }

        override fun onSecurityException(account: Account, extras: Bundle, authority: String, syncResult: SyncResult) {
            logger.log(Level.WARNING, "Security exception for $account/$authority")
        }

        override fun onSyncCanceled() {
            logger.info("Sync adapter requested cancellation – won't cancel sync, but also won't block sync framework anymore")

            // unblock sync framework
            waitScope.cancel()
        }

        override fun onSyncCanceled(thread: Thread) = onSyncCanceled()

    }

}

// exported sync adapter services; we need a separate class for each authority
class CalendarsSyncAdapterService: SyncAdapterService()
class ContactsSyncAdapterService: SyncAdapterService()
class JtxSyncAdapterService: SyncAdapterService()
class OpenTasksSyncAdapterService: SyncAdapterService()
class TasksOrgSyncAdapterService: SyncAdapterService()