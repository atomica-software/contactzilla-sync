/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomica.contactzillasync.sync.worker

import android.accounts.Account
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.hilt.work.HiltWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.atomica.contactzillasync.sync.SyncDataType
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * Handles scheduled sync requests.
 *
 * The different periodic sync workers each carry a unique work name composed of the account and
 * authority which they are responsible for. For each account there will be multiple dedicated periodic
 * sync workers for each authority. See [PeriodicSyncWorker.workerName] for more information.
 *
 * Deferrable: yes (periodic)
 *
 * Expedited: no (→ no [getForegroundInfo])
 *
 * Long-running: no
 *
 * **Important:** If this class is renamed (or its package is changed), already enqueued workers won't
 * run anymore because WorkManager references the work by the full class name.
 */
@HiltWorker
class PeriodicSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : BaseSyncWorker(appContext, workerParams) {

    @AssistedFactory
    @VisibleForTesting
    interface Factory {
        fun create(appContext: Context, workerParams: WorkerParameters): PeriodicSyncWorker
    }

    companion object {

        /**
         * Unique work name of this worker. Can also be used as tag.
         *
         * Mainly used to query [WorkManager] for work state (by unique work name or tag).
         *
         * @param account   the account this worker is running for
         * @param dataType  data type to be synchronized
         *
         * @return Name of this worker composed as "periodic-sync $authority ${account.type}/${account.name}"
         */
        fun workerName(account: Account, dataType: SyncDataType): String =
            "periodic-sync $dataType ${account.type}/${account.name}"

    }

}