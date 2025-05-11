/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.account

import android.accounts.Account
import android.content.Context
import androidx.work.WorkInfo
import com.messageconcept.peoplesyncclient.db.Service
import com.messageconcept.peoplesyncclient.servicedetection.RefreshCollectionsWorker
import com.messageconcept.peoplesyncclient.sync.SyncDataType
import com.messageconcept.peoplesyncclient.sync.worker.OneTimeSyncWorker
import com.messageconcept.peoplesyncclient.sync.worker.SyncWorkerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class AccountProgressUseCase @Inject constructor(
    @ApplicationContext val context: Context,
    private val syncWorkerManager: SyncWorkerManager
) {

    operator fun invoke(
        account: Account,
        serviceFlow: Flow<Service?>,
        dataTypes: Iterable<SyncDataType>
    ): Flow<AccountProgress> {
        val serviceRefreshing = isServiceRefreshing(serviceFlow)
        val syncPending = isSyncPending(account, dataTypes)
        val syncRunning = isSyncRunning(account, dataTypes)

        return combine(serviceRefreshing, syncPending, syncRunning) { refreshing, pending, syncing ->
            when {
                refreshing || syncing -> AccountProgress.Active
                pending -> AccountProgress.Pending
                else -> AccountProgress.Idle
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun isServiceRefreshing(serviceFlow: Flow<Service?>): Flow<Boolean> =
        serviceFlow.flatMapLatest { service ->
            if (service == null)
                flowOf(false)
            else
                RefreshCollectionsWorker.existsFlow(context, RefreshCollectionsWorker.workerName(service.id))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun isSyncPending(account: Account, dataTypes: Iterable<SyncDataType>): Flow<Boolean> =
        syncWorkerManager.hasAnyFlow(
            workStates = listOf(WorkInfo.State.ENQUEUED),
            account = account,
            dataTypes = dataTypes,
            whichTag = { _, authority ->
                // we are only interested in pending OneTimeSyncWorkers because there's always a pending PeriodicSyncWorker
                OneTimeSyncWorker.workerName(account, authority)
            }
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun isSyncRunning(account: Account, dataTypes: Iterable<SyncDataType>): Flow<Boolean> =
        syncWorkerManager.hasAnyFlow(
            workStates = listOf(WorkInfo.State.RUNNING),
            account = account,
            dataTypes = dataTypes
        )

}