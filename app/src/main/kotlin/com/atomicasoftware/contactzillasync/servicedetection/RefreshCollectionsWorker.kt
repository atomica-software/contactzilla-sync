/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.servicedetection

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import at.bitfire.dav4jvm.exception.UnauthorizedException
import com.atomicasoftware.contactzillasync.R
import com.atomicasoftware.contactzillasync.network.HttpClient
import com.atomicasoftware.contactzillasync.repository.DavServiceRepository
import com.atomicasoftware.contactzillasync.servicedetection.RefreshCollectionsWorker.Companion.ARG_SERVICE_ID
import com.atomicasoftware.contactzillasync.sync.account.InvalidAccountException
import com.atomicasoftware.contactzillasync.ui.DebugInfoActivity
import com.atomicasoftware.contactzillasync.ui.NotificationRegistry
import com.atomicasoftware.contactzillasync.ui.account.AccountSettingsActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runInterruptible
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Refreshes list of home sets and their respective collections of a service type (CardDAV or CalDAV).
 * Called from UI, when user wants to refresh all collections of a service.
 *
 * Input data:
 *
 *  - [ARG_SERVICE_ID]: service ID
 *
 * It queries all existing homesets and/or collections and then:
 *  - updates resources with found properties (overwrites without comparing)
 *  - adds resources if new ones are detected
 *  - removes resources if not found 40x (delete locally)
 *
 * Expedited: yes (always initiated by user)
 *
 * Long-running: no
 *
 * @throws IllegalArgumentException when there's no service with the given service ID
 */
@HiltWorker
class RefreshCollectionsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val collectionListRefresherFactory: CollectionListRefresher.Factory,
    private val httpClientBuilder: HttpClient.Builder,
    private val logger: Logger,
    private val notificationRegistry: NotificationRegistry,
    serviceRepository: DavServiceRepository
): CoroutineWorker(appContext, workerParams) {

    companion object {

        const val ARG_SERVICE_ID = "serviceId"
        const val WORKER_TAG = "refreshCollectionsWorker"

        /**
         * Uniquely identifies a refresh worker. Useful for stopping work, or querying its state.
         *
         * @param serviceId     what service (CardDAV) the worker is running for
         */
        fun workerName(serviceId: Long): String = "$WORKER_TAG-$serviceId"

        /**
         * Requests immediate refresh of a given service. If not running already. this will enqueue
         * a [RefreshCollectionsWorker].
         *
         * @param serviceId     serviceId which is to be refreshed
         * @return Pair with
         *
         * 1. worker name,
         * 2. operation of [WorkManager.enqueueUniqueWork] (can be used to wait for completion)
         *
         * @throws IllegalArgumentException when there's no service with this ID
         */
        fun enqueue(context: Context, serviceId: Long): Pair<String, Operation> {
            val name = workerName(serviceId)
            val arguments = Data.Builder()
                .putLong(ARG_SERVICE_ID, serviceId)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<RefreshCollectionsWorker>()
                .addTag(name)
                .setInputData(arguments)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            return Pair(
                name,
                WorkManager.getInstance(context).enqueueUniqueWork(
                    name,
                    ExistingWorkPolicy.KEEP,    // if refresh is already running, just continue that one
                    workRequest
                )
            )
        }

        /**
         * Observes whether a refresh worker with given service id and state exists.
         *
         * @param workerName    name of worker to find
         * @param workState     state of worker to match
         *
         * @return flow that emits `true` if worker with matching state was found (otherwise `false`)
         */
        fun existsFlow(context: Context, workerName: String, workState: WorkInfo.State = WorkInfo.State.RUNNING) =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(workerName).map { workInfoList ->
                workInfoList.any { workInfo -> workInfo.state == workState }
            }

    }

    val serviceId: Long = inputData.getLong(ARG_SERVICE_ID, -1)
    val service = serviceRepository.getBlocking(serviceId)
    val account = service?.let { service ->
        Account(service.accountName, applicationContext.getString(R.string.account_type))
    }

    override suspend fun doWork(): Result {
        if (service == null || account == null) {
            logger.warning("Missing service or account with service ID: $serviceId")
            return Result.failure()
        }

        try {
            logger.info("Refreshing ${service.type} collections of service #$service")

            // cancel previous notification
            NotificationManagerCompat.from(applicationContext)
                .cancel(serviceId.toString(), NotificationRegistry.NOTIFY_REFRESH_COLLECTIONS)

            // create authenticating OkHttpClient (credentials taken from account settings)
            httpClientBuilder
                .fromAccount(account)
                .build()
                .use { httpClient ->
                    runInterruptible {
                        val httpClient = httpClient.okHttpClient
                        val refresher = collectionListRefresherFactory.create(service, httpClient)

                        // refresh home set list (from principal url)
                        service.principal?.let { principalUrl ->
                            logger.fine("Querying principal $principalUrl for home sets")
                            refresher.discoverHomesets(principalUrl)
                        }

                        // refresh home sets and their member collections
                        refresher.refreshHomesetsAndTheirCollections()

                        // also refresh collections without a home set
                        refresher.refreshHomelessCollections()

                        // Lastly, refresh the principals (collection owners)
                        refresher.refreshPrincipals()
                    }
                }

        } catch(e: InvalidAccountException) {
            logger.log(Level.SEVERE, "Invalid account", e)
            return Result.failure()
        } catch (e: UnauthorizedException) {
            logger.log(Level.SEVERE, "Not authorized (anymore)", e)
            // notify that we need to re-authenticate in the account settings
            val settingsIntent = Intent(applicationContext, AccountSettingsActivity::class.java)
                .putExtra(AccountSettingsActivity.EXTRA_ACCOUNT, account)
            notifyRefreshError(
                applicationContext.getString(R.string.sync_error_authentication_failed),
                settingsIntent
            )
            return Result.failure()
        } catch(e: Exception) {
            logger.log(Level.SEVERE, "Couldn't refresh collection list", e)

            val debugIntent = DebugInfoActivity.IntentBuilder(applicationContext)
                .withCause(e)
                .withAccount(account)
                .build()
            notifyRefreshError(
                applicationContext.getString(R.string.refresh_collections_worker_refresh_couldnt_refresh),
                debugIntent
            )
            return Result.failure()
        }

        // Success
        return Result.success()
    }

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
            .build()
        return ForegroundInfo(NotificationRegistry.NOTIFY_SYNC_EXPEDITED, notification)
    }

    private fun notifyRefreshError(contentText: String, contentIntent: Intent) {
        notificationRegistry.notifyIfPossible(NotificationRegistry.NOTIFY_REFRESH_COLLECTIONS, tag = serviceId.toString()) {
            NotificationCompat.Builder(applicationContext, notificationRegistry.CHANNEL_GENERAL)
                .setSmallIcon(R.drawable.ic_sync_problem_notify)
                .setContentTitle(applicationContext.getString(R.string.refresh_collections_worker_refresh_failed))
                .setContentText(contentText)
                .setContentIntent(
                    TaskStackBuilder.create(applicationContext)
                        .addNextIntentWithParentStack(contentIntent)
                        .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
                .setSubText(account?.name)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .build()
        }
    }

}