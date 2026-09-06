/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.startup

import com.atomicasoftware.contactzillasync.repository.AccountRepository
import com.atomicasoftware.contactzillasync.settings.AccountSettings
import com.atomicasoftware.contactzillasync.settings.SettingsManager
import com.atomicasoftware.contactzillasync.startup.StartupPlugin.Companion.PRIORITY_DEFAULT
import com.atomicasoftware.contactzillasync.sync.AutomaticSyncManager
import com.atomicasoftware.contactzillasync.sync.SyncDataType
import com.atomicasoftware.contactzillasync.sync.worker.SyncWorkerManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes sure that every existing account actually has the automatic synchronization set up that its
 * settings ask for.
 *
 * Accounts are normally set up completely by [AccountRepository.createBlocking]. This plugin exists for
 * accounts that were created before that was the case, and to pick up a sync interval that was changed
 * by the MDM after the accounts had already been created. Without it, such accounts would never sync in
 * the background until someone opened the account settings and set a sync interval by hand.
 */
@Singleton
class AutomaticSyncSetup @Inject constructor(
    private val accountRepository: AccountRepository,
    private val accountSettingsFactory: AccountSettings.Factory,
    private val automaticSyncManager: AutomaticSyncManager,
    private val logger: Logger,
    private val settingsManager: SettingsManager,
    private val syncWorkerManager: SyncWorkerManager
): StartupPlugin, SettingsManager.OnChangeListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onAppCreate() {
        // nothing to do synchronously
    }

    override fun priority() = PRIORITY_DEFAULT

    override suspend fun onAppCreateAsync() {
        // Runs on every app start, including when the process is only started in the background (for
        // instance by AccountsCleanupWorker), so no user interaction is needed.
        updateAccounts()

        // Pick up a sync interval that is changed by the MDM while the app is running.
        settingsManager.addOnChangeListener(this)
    }

    override fun priorityAsync() = PRIORITY_DEFAULT + 20   // after the managed accounts have been created

    override fun onSettingsChanged() {
        scope.launch {
            updateAccounts()
        }
    }

    /**
     * Updates automatic synchronization of all accounts whose scheduled periodic sync doesn't match their
     * settings anymore.
     *
     * Accounts that are already scheduled correctly are left alone, so that repeatedly calling this doesn't
     * interfere with a periodic sync that is up and running.
     */
    private fun updateAccounts() {
        for (account in accountRepository.getAll())
            try {
                val accountSettings = accountSettingsFactory.create(account)
                val outdated = SyncDataType.entries.any { dataType ->
                    val wanted = accountSettings.getSyncInterval(dataType)
                        ?.let(syncWorkerManager::effectivePeriodicInterval)
                    wanted != syncWorkerManager.getPeriodicInterval(account, dataType)
                }
                if (outdated) {
                    logger.info("Setting up automatic sync for ${account.name}")
                    automaticSyncManager.updateAutomaticSync(account)
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't set up automatic sync for ${account.name}", e)
            }
    }


    @Module
    @InstallIn(SingletonComponent::class)
    interface AutomaticSyncSetupModule {
        @Binds
        @IntoSet
        fun automaticSyncSetup(impl: AutomaticSyncSetup): StartupPlugin
    }

}
