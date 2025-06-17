/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomica.contactzillasync.sync.worker

import android.accounts.Account
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.atomica.contactzillasync.R
import com.atomica.contactzillasync.sync.SyncDataType
import com.atomica.contactzillasync.ui.NotificationRegistry
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * One-time sync worker.
 *
 * Expedited: yes
 *
 * Long-running: no
 */
@HiltWorker
class OneTimeSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : BaseSyncWorker(appContext, workerParams) {

    /**
     * Used by WorkManager to show a foreground service notification for expedited jobs on Android <12.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, notificationRegistry.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_foreground_notify)
            .setContentTitle(applicationContext.getString(R.string.foreground_service_notify_title))
            .setContentText(applicationContext.getString(R.string.foreground_service_notify_text))
            .setStyle(NotificationCompat.BigTextStyle())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
        return ForegroundInfo(NotificationRegistry.NOTIFY_SYNC_EXPEDITED, notification)
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
         * @return Name of  this worker composed as "onetime-sync $authority ${account.type}/${account.name}"
         */
        fun workerName(account: Account, dataType: SyncDataType): String =
            "onetime-sync $dataType ${account.type}/${account.name}"

    }

}