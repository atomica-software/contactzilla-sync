/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomica.contactzillasync.sync.worker

import android.accounts.Account
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkRequest
import com.atomica.contactzillasync.sync.ResyncType
import com.atomica.contactzillasync.sync.SyncDataType
import com.atomica.contactzillasync.sync.worker.BaseSyncWorker.Companion.INPUT_ACCOUNT_NAME
import com.atomica.contactzillasync.sync.worker.BaseSyncWorker.Companion.INPUT_ACCOUNT_TYPE
import com.atomica.contactzillasync.sync.worker.BaseSyncWorker.Companion.INPUT_DATA_TYPE
import com.atomica.contactzillasync.sync.worker.BaseSyncWorker.Companion.INPUT_MANUAL
import com.atomica.contactzillasync.sync.worker.BaseSyncWorker.Companion.INPUT_RESYNC
import com.atomica.contactzillasync.sync.worker.BaseSyncWorker.Companion.INPUT_UPLOAD
import com.atomica.contactzillasync.sync.worker.BaseSyncWorker.Companion.RESYNC_ENTRIES
import com.atomica.contactzillasync.sync.worker.BaseSyncWorker.Companion.RESYNC_LIST
import com.atomica.contactzillasync.sync.worker.BaseSyncWorker.Companion.commonTag
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import javax.inject.Inject

/**
 * For building and managing synchronization workers (both one-time and periodic).
 *
 * One-time sync workers can be enqueued. Periodic sync workers can be enabled and disabled.
 */
class SyncWorkerManager @Inject constructor(
    @ApplicationContext val context: Context,
    val logger: Logger,
) {

    // one-time sync workers

    /**
     * Builds a one-time sync worker for a specific account and authority.
     *
     * Arguments: see [enqueueOneTime]
     *
     * @return one-time sync work request for the given arguments
     */
    fun buildOneTime(
        account: Account,
        dataType: SyncDataType,
        manual: Boolean = false,
        resync: ResyncType? = null,
        fromUpload: Boolean = false
    ): OneTimeWorkRequest {
        // worker arguments
        val argumentsBuilder = Data.Builder()
            .putString(INPUT_DATA_TYPE, dataType.toString())
            .putString(INPUT_ACCOUNT_NAME, account.name)
            .putString(INPUT_ACCOUNT_TYPE, account.type)

        if (manual)
            argumentsBuilder.putBoolean(INPUT_MANUAL, true)

        when (resync) {
            ResyncType.RESYNC_ENTRIES -> argumentsBuilder.putInt(INPUT_RESYNC, RESYNC_ENTRIES)
            ResyncType.RESYNC_LIST -> argumentsBuilder.putInt(INPUT_RESYNC, RESYNC_LIST)
            else -> { /* no explicit re-synchronization */ }
        }

        argumentsBuilder.putBoolean(INPUT_UPLOAD, fromUpload)

        // build work request
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)   // require a network connection
            .build()
        return OneTimeWorkRequestBuilder<OneTimeSyncWorker>()
            .addTag(OneTimeSyncWorker.workerName(account, dataType))
            .addTag(commonTag(account, dataType))
            .setInputData(argumentsBuilder.build())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,   // 30 sec
                TimeUnit.MILLISECONDS
            )
            .setConstraints(constraints)

            /* OneTimeSyncWorker is started by user or sync framework when there are local changes.
            In both cases, synchronization should be done as soon as possible, so we set expedited. */
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)

            // build work request
            .build()
    }

    /**
     * Requests immediate synchronization of an account with a specific authority.
     *
     * If there is no currently running one-time sync, the sync is enqueued normally.
     *
     * If there is a currently running one-time sync, another sync is appended to make sure
     * a complete sync is run. This method makes however sure that there's only _one_
     * further sync in the queue.
     *
     * @param account       account to sync
     * @param dataType      type of data to synchronize
     * @param manual        user-initiated sync (ignores network checks)
     * @param resync        whether to request (full) re-synchronization (`null` for normal sync)
     * @param fromUpload    whether this sync is initiated by a local change
     * @param fromPush      whether this sync is initiated by a push notification
     *
     * @return existing or newly created worker name
     */
    fun enqueueOneTime(
        account: Account,
        dataType: SyncDataType,
        manual: Boolean = false,
        resync: ResyncType? = null,
        fromUpload: Boolean = false,
        fromPush: Boolean = false
    ): String {
        logger.info("Enqueueing unique worker for account=$account, dataType=$dataType, manual=$manual, resync=$resync, fromUpload=$fromUpload, fromPush=$fromPush")

        // enqueue and start syncing
        val name = OneTimeSyncWorker.workerName(account, dataType)
        val request = buildOneTime(
            account = account,
            dataType = dataType,
            manual = manual,
            resync = resync,
            fromUpload = fromUpload
        )

        /* We want to append only one work request, regardless of how many sync requests came in.
        So we have to append the work one time, and as soon as there is already a pending
        appended work, stop adding more work. */

        val workManager = WorkManager.getInstance(context)
        synchronized(SyncWorkerManager::class.java) {
            val currentWork = workManager.getWorkInfosForUniqueWork(name).get()
            val alreadyAppended = currentWork.any {
                it.state in setOf(WorkInfo.State.BLOCKED, WorkInfo.State.ENQUEUED)
            }
            if (!alreadyAppended) {
                val op = workManager.enqueueUniqueWork(name, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
                // for synchronization: wait until work is actually enqueued
                op.result
            } else
                logger.fine("Another one-time sync already waiting, not adding more of $name")
        }

        return name
    }

    /**
     * Requests immediate synchronization of an account with all applicable
     * authorities (contacts, calendars, …).
     *
     * Arguments: see [enqueueOneTime]
     */
    fun enqueueOneTimeAllAuthorities(
        account: Account,
        manual: Boolean = false,
        resync: ResyncType? = null,
        fromUpload: Boolean = false,
        fromPush: Boolean = false
    ) {
        for (dataType in SyncDataType.entries)
            enqueueOneTime(
                account = account,
                dataType = dataType,
                manual = manual,
                resync = resync,
                fromUpload = fromUpload,
                fromPush = fromPush
            )
    }


    // periodic sync workers

    /**
     * Builds a periodic sync worker for a specific account and authority.
     *
     * Arguments: see [enablePeriodic]
     *
     * @return periodic sync work request for the given arguments
     */
    fun buildPeriodic(account: Account, dataType: SyncDataType, interval: Long, syncWifiOnly: Boolean): PeriodicWorkRequest {
        val arguments = Data.Builder()
            .putString(INPUT_DATA_TYPE, dataType.toString())
            .putString(INPUT_ACCOUNT_NAME, account.name)
            .putString(INPUT_ACCOUNT_TYPE, account.type)
            .build()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (syncWifiOnly)
                    NetworkType.UNMETERED
                else
                    NetworkType.CONNECTED
            ).build()
        return PeriodicWorkRequestBuilder<PeriodicSyncWorker>(interval, TimeUnit.SECONDS)
            .addTag(PeriodicSyncWorker.workerName(account, dataType))
            .addTag(commonTag(account, dataType))
            .setInputData(arguments)
            .setConstraints(constraints)
            .build()
    }

    /**
     * Activate periodic synchronization of an account with a specific authority.
     *
     * @param account    account to sync
     * @param dataType   type of data to synchronize
     * @param interval   interval between recurring syncs in seconds
     * @return operation object to check when and whether activation was successful
     */
    fun enablePeriodic(account: Account, dataType: SyncDataType, interval: Long, syncWifiOnly: Boolean): Operation {
        logger.fine("Updating periodic worker for account=$account, dataType=$dataType, interval=$interval, syncWifiOnly=$syncWifiOnly")
        val workRequest = buildPeriodic(account, dataType, interval, syncWifiOnly)
        return WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PeriodicSyncWorker.workerName(account, dataType),
            // if a periodic sync exists already, we want to update it with the new interval
            // and/or new required network type (applies on next iteration of periodic worker)
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    /**
     * Disables periodic synchronization of an account for a specific authority.
     *
     * @param account     account to sync
     * @param dataType    type of data to synchronize
     * @return operation object to check process state of work cancellation
     */
    fun disablePeriodic(account: Account, dataType: SyncDataType): Operation {
        logger.fine("Disabling periodic worker for account=$account, dataType=$dataType")
        return WorkManager.getInstance(context)
            .cancelUniqueWork(PeriodicSyncWorker.workerName(account, dataType))
    }


    // common / helpers

    /**
     * Stops running sync workers and removes pending sync workers from queue, for all authorities.
     */
    fun cancelAllWork(account: Account) {
        val workManager = WorkManager.getInstance(context)
        for (dataType in SyncDataType.entries) {
            workManager.cancelUniqueWork(OneTimeSyncWorker.workerName(account, dataType))
            workManager.cancelUniqueWork(PeriodicSyncWorker.workerName(account, dataType))
        }
    }

    /**
     * Observes whether >0 sync workers (both [PeriodicSyncWorker] and [OneTimeSyncWorker])
     * exist, belonging to given account and authorities, and which are/is in the given worker state.
     *
     * @param workStates   list of states of workers to match
     * @param account      the account which the workers belong to
     * @param dataTypes    data types of sync work
     * @param whichTag     function to generate tag that should be observed for given account and authority
     *
     * @return flow that emits `true` if at least one worker with matching query was found; `false` otherwise
     */
    fun hasAnyFlow(
        workStates: List<WorkInfo.State>,
        account: Account? = null,
        dataTypes: Iterable<SyncDataType>? = null,
        whichTag: (account: Account, dataType: SyncDataType) -> String = { account, dataType ->
            commonTag(account, dataType)
        }
    ): Flow<Boolean> {
        val workQuery = WorkQuery.Builder.fromStates(workStates)
        if (account != null && dataTypes != null)
            workQuery.addTags(
                dataTypes.map { dataType -> whichTag(account, dataType) }
            )
        return WorkManager.getInstance(context)
            .getWorkInfosFlow(workQuery.build())
            .map { workInfoList ->
                workInfoList.isNotEmpty()
            }
    }

}