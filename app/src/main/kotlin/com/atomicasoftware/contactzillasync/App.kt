/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.atomicasoftware.contactzillasync.log.LogManager
import com.atomicasoftware.contactzillasync.startup.StartupPlugin
import com.atomicasoftware.contactzillasync.settings.ManagedSettings
import com.atomicasoftware.contactzillasync.sync.account.AccountsCleanupWorker
import com.atomicasoftware.contactzillasync.ui.UiUtils
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.logging.Logger
import javax.inject.Inject

@HiltAndroidApp
class App: Application(), Configuration.Provider {

    @Inject
    lateinit var logger: Logger

    /**
     * Creates the [LogManager] singleton and thus initializes logging.
     */
    @Inject
    lateinit var logManager: LogManager

    @Inject
    lateinit var plugins: Set<@JvmSuppressWildcards StartupPlugin>

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    @Inject
    lateinit var managedSettings: ManagedSettings

    override fun onCreate() {
        super.onCreate()

        logger.fine("Logging using LogManager $logManager")

        // set light/dark mode
        UiUtils.updateTheme(this)   // when this is called in the asynchronous thread below, it recreates
                                 // some current activity and causes an IllegalStateException in rare cases

        // run startup plugins (sync)
        for (plugin in plugins.sortedBy { it.priority() }) {
            logger.fine("Running startup plugin: $plugin (onAppCreate)")
            plugin.onAppCreate()
        }

        // don't block UI for some background checks
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.Default) {
            // clean up orphaned accounts in DB from time to time
            AccountsCleanupWorker.enable(this@App)

            // create/update app shortcuts
            UiUtils.updateShortcuts(this@App)

            // trigger account updates when managed settings have changed
            managedSettings.updateAccounts()
            // trigger a (one-time) migration of the account settings for existing accounts
            managedSettings.loadNewAccountSettings()

            // run startup plugins (async)
            for (plugin in plugins.sortedBy { it.priorityAsync() }) {
                logger.fine("Running startup plugin: $plugin (onAppCreateAsync)")
                plugin.onAppCreateAsync()
            }
        }
    }

}