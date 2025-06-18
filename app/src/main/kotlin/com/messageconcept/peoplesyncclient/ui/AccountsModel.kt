/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomica.contactzillasync.ui

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED
import android.content.IntentFilter
import android.content.pm.PackageManager.NameNotFoundException
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.PowerManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.core.content.getSystemService
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.ViewModel
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.atomica.contactzillasync.db.AppDatabase
import com.atomica.contactzillasync.repository.AccountRepository
import com.atomica.contactzillasync.servicedetection.RefreshCollectionsWorker
import com.atomica.contactzillasync.settings.ManagedSettings
import com.atomica.contactzillasync.sync.SyncDataType
import com.atomica.contactzillasync.sync.worker.BaseSyncWorker
import com.atomica.contactzillasync.sync.worker.OneTimeSyncWorker
import com.atomica.contactzillasync.sync.worker.SyncWorkerManager
import com.atomica.contactzillasync.ui.account.AccountProgress
import com.atomica.contactzillasync.ui.intro.IntroPage
import com.atomica.contactzillasync.ui.intro.IntroPageFactory
import com.atomica.contactzillasync.util.broadcastReceiverFlow
import com.atomica.contactzillasync.util.packageChangedFlow
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.util.logging.Logger

@HiltViewModel(assistedFactory = AccountsModel.Factory::class)
class AccountsModel @AssistedInject constructor(
    @Assisted private val syncAccountsOnInit: Boolean,
    private val accountRepository: AccountRepository,
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    introPageFactory: IntroPageFactory,
    private val logger: Logger,
    private val managedSettings: ManagedSettings,
    private val syncWorkerManager: SyncWorkerManager
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(syncAccountsOnInit: Boolean): AccountsModel
    }


    // Accounts UI state

    enum class FABStyle {
        WithText,
        Standard,
        None
    }

    data class AccountInfo(
        val name: Account,
        val progress: AccountProgress
    )

    private val accounts = accountRepository.getAllFlow()
    val showAddAccount: Flow<FABStyle> = accounts.map {
        if (managedSettings.hasManagedAccounts())
            FABStyle.None
        else if (it.isEmpty())
            FABStyle.WithText
        else
            FABStyle.Standard
    }
    val showSyncAll: Flow<Boolean> = accounts.map { it.isNotEmpty() }

    private val workManager = WorkManager.getInstance(context)
    private val runningWorkers = workManager.getWorkInfosFlow(WorkQuery.fromStates(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING))

    val accountInfos: Flow<List<AccountInfo>> = combine(accounts, runningWorkers) { accounts, workInfos ->
        val collator = Collator.getInstance()

        accounts
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
            .map { account ->
                val services = db.serviceDao().getIdsByAccountAsync(account.name)
                val progress = when {
                    workInfos.any { info ->
                        info.state == WorkInfo.State.RUNNING && (
                                services.any { serviceId ->
                                    info.tags.contains(RefreshCollectionsWorker.workerName(serviceId))
                                } || SyncDataType.entries.any { dataType ->
                                    info.tags.contains(BaseSyncWorker.commonTag(account, dataType))
                                }
                            )
                    } -> AccountProgress.Active

                    workInfos.any { info ->
                        info.state == WorkInfo.State.ENQUEUED && SyncDataType.entries.any { dataType ->
                            info.tags.contains(OneTimeSyncWorker.workerName(account, dataType))
                        }
                    } -> AccountProgress.Pending

                    else -> AccountProgress.Idle
                }

                AccountInfo(account, progress)
            }
    }


    // other UI state

    val showAppIntro: Flow<Boolean> = flow<Boolean> {
        val anyShowAlwaysPage = introPageFactory.introPages.any { introPage ->
            val policy = introPage.getShowPolicy()
            logger.fine("Intro page ${introPage::class.java.name} policy = $policy")

            policy == IntroPage.ShowPolicy.SHOW_ALWAYS
        }

        emit(anyShowAlwaysPage)
    }.flowOn(Dispatchers.Default)


    // warnings

    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private val powerManager: PowerManager = context.getSystemService<PowerManager>()!!

    /** whether to consider managed mode **/
    val isManaged =
        broadcastReceiverFlow(
            context = context,
            filter = IntentFilter(ACTION_APPLICATION_RESTRICTIONS_CHANGED),
            immediate = true
        ).map { managedSettings.hasManagedAccounts() }

    /** the entity managing the accounts (for display in UI) **/
    val managedBy =
        broadcastReceiverFlow(
            context = context,
            filter = IntentFilter(ACTION_APPLICATION_RESTRICTIONS_CHANGED),
            immediate = true
        ).map { managedSettings.getManagedBy() }

    /** whether a usable network connection is available (sync framework won't run synchronization otherwise) */
    val networkAvailable = callbackFlow<Boolean> {
        val networkCallback = object: ConnectivityManager.NetworkCallback() {
            val availableNetworks = hashSetOf<Network>()

            override fun onAvailable(network: Network) {
                availableNetworks += network
                update()
            }

            override fun onLost(network: Network) {
                availableNetworks -= network
                update()
            }

            private fun update() {
                trySend(availableNetworks.isNotEmpty())
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    /** whether battery saver is active */
    val batterySaverActive =
        broadcastReceiverFlow(
            context = context,
            filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            immediate = true
        ).map { powerManager.isPowerSaveMode }

    /** whether data saver is restricting background synchronization ([ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED]) */
    val dataSaverEnabled =
        broadcastReceiverFlow(
            context = context,
            filter = IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED),
            immediate = true
        ).map { connectivityManager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED }

    /** whether storage is low (prevents sync framework from running synchronization) */
    @Suppress("DEPRECATION")
    val storageLow =
        broadcastReceiverFlow(
            context = context,
            filter = IntentFilter().apply {
                addAction(Intent.ACTION_DEVICE_STORAGE_LOW)
                addAction(Intent.ACTION_DEVICE_STORAGE_OK)
            },
            immediate = false     // "storage low" intent is sticky
        ).map { intent ->
            when (intent.action) {
                Intent.ACTION_DEVICE_STORAGE_LOW -> true
                else -> false
            }
        }

    /** whether the calendar storage is missing or disabled */
    val calendarStorageDisabled = packageChangedFlow(context).map {
        !contentProviderAvailable(CalendarContract.AUTHORITY)
    }

    /** whether the calendar storage is missing or disabled */
    val contactsStorageDisabled = packageChangedFlow(context).map {
        !contentProviderAvailable(ContactsContract.AUTHORITY)
    }


    init {
        if (syncAccountsOnInit)
            syncAllAccounts()
    }

    
    // actions

    fun syncAllAccounts() {
        // report shortcut action to system
        ShortcutManagerCompat.reportShortcutUsed(context, UiUtils.SHORTCUT_SYNC_ALL)

        // Enqueue sync worker for all accounts and authorities. Will sync once internet is available
        for (account in accountRepository.getAll())
            syncWorkerManager.enqueueOneTimeAllAuthorities(account, manual = true)
    }


    // helpers

    fun contentProviderAvailable(authority: String): Boolean =
        try {
            // resolveContentProvider returns null if the provider app is disabled or missing;
            // so we can't distinguish between "disabled" and "not found"
            context.packageManager.resolveContentProvider(authority, 0) != null
        } catch (_: NameNotFoundException) {
            logger.fine("$authority provider app not found")
            false
        }

}