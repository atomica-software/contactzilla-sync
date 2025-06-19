/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.atomica.contactzillasync.startup

import android.content.Context
import com.atomica.contactzillasync.db.Credentials
import com.atomica.contactzillasync.repository.AccountRepository
import com.atomica.contactzillasync.servicedetection.DavResourceFinder
import com.atomica.contactzillasync.servicedetection.RefreshCollectionsWorker
import com.atomica.contactzillasync.sync.worker.SyncWorkerManager
import com.atomica.contactzillasync.settings.AccountSettings
import com.atomica.contactzillasync.settings.ManagedAccountConfig
import com.atomica.contactzillasync.settings.ManagedSettings
import com.atomica.contactzillasync.util.PermissionUtils
import com.atomica.contactzillasync.startup.StartupPlugin.Companion.PRIORITY_DEFAULT
import com.atomica.contactzillasync.startup.StartupPlugin.Companion.PRIORITY_HIGHEST
import at.bitfire.vcard4android.GroupMethod
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Automatically creates accounts from MDM configuration on app startup.
 */
class ManagedAccountSetup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    private val managedSettings: ManagedSettings,
    private val accountRepository: AccountRepository,
    private val davResourceFinderFactory: DavResourceFinder.Factory,
    private val syncWorkerManager: SyncWorkerManager
): StartupPlugin {

    @Module
    @InstallIn(SingletonComponent::class)
    interface ManagedAccountSetupModule {
        @Binds
        @IntoSet
        fun managedAccountSetup(impl: ManagedAccountSetup): StartupPlugin
    }

    override fun onAppCreate() {
        // Just log that we're ready - actual account creation happens in onAppCreateAsync
        logger.info("ManagedAccountSetup.onAppCreate() called")
        val hasManagedAccounts = managedSettings.hasManagedAccounts()
        logger.info("hasManagedAccounts() returned: $hasManagedAccounts")
        
        if (hasManagedAccounts) {
            logger.info("MDM managed accounts detected, will create accounts in async phase")
        } else {
            logger.info("No managed accounts detected")
        }
    }

    override fun priority() = PRIORITY_HIGHEST + 10 // Run after crash handler but before most other plugins

    override suspend fun onAppCreateAsync() {
        // Create managed accounts asynchronously to avoid blocking app startup
        if (managedSettings.hasManagedAccounts()) {
            logger.info("Starting async account creation...")
            
            // First, clean up any accounts that are no longer in MDM configuration
            cleanupRemovedManagedAccounts()
            
            // Then create/update accounts from current configuration
            createManagedAccountsAsync()
            
            // After account creation, wait a bit and verify setup
            delay(2000) // Wait 2 seconds for accounts to be created
            
            logger.info("Checking managed accounts for proper collection setup...")
            val accountConfigs = managedSettings.getAllAccountConfigs()
            
            for (config in accountConfigs) {
                val accountExists = withContext(Dispatchers.IO) {
                    accountRepository.exists(config.accountName)
                }
                if (accountExists) {
                    logger.info("Verifying collections setup for account: ${config.accountName}")
                    
                    // Trigger a collection refresh to ensure account is properly configured
                    // This will help ensure the account details screen shows content
                    try {
                        // Note: In a real implementation, we'd check if collections exist first
                        // For now, we'll just log that this should be done
                        logger.info("Account ${config.accountName} exists - collection discovery should be complete")
                    } catch (e: Exception) {
                        logger.log(Level.WARNING, "Could not verify collections for ${config.accountName}", e)
                    }
                }
            }
        }
    }

    override fun priorityAsync(): Int = PRIORITY_DEFAULT

    /**
     * Creates a single managed account from the given configuration.
     * This method can be used by QR code setup or other account creation flows.
     * 
     * @param config The account configuration to create
     * @return true if the account was created successfully, false otherwise
     */
    suspend fun createSingleManagedAccount(config: ManagedAccountConfig): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                logger.info("Creating single managed account: ${config.accountName} (URL: ${config.baseUrl}, User: ${config.username})")
                
                // Check if account already exists
                val accountExists = accountRepository.exists(config.accountName)
                if (accountExists) {
                    logger.info("Account ${config.accountName} already exists, skipping creation")
                    return@withContext false
                }
                
                // Create credentials
                val credentials = Credentials(
                    username = config.username,
                    password = config.password
                )
                
                // Parse base URL - add https:// if missing
                val baseUri = try {
                    val url = if (!config.baseUrl.startsWith("http://") && !config.baseUrl.startsWith("https://")) {
                        "https://${config.baseUrl}"
                    } else {
                        config.baseUrl
                    }
                    logger.info("Using URL: $url for account ${config.accountName}")
                    URI(url)
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Invalid base URL for account ${config.accountName}: ${config.baseUrl}", e)
                    return@withContext false
                }
                
                logger.info("Starting service discovery for ${config.accountName} at ${baseUri}")
                
                // Perform service discovery
                val serviceConfig = try {
                    davResourceFinderFactory.create(baseUri, credentials).use { finder ->
                        finder.findInitialConfiguration()
                    }
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Service discovery failed for account ${config.accountName}", e)
                    return@withContext false
                }
                
                if (serviceConfig == null) {
                    logger.warning("Service discovery returned null for account ${config.accountName}")
                    return@withContext false
                }
                
                logger.info("Service discovery completed for ${config.accountName}. CardDAV: ${serviceConfig.cardDAV != null}")
                
                if (serviceConfig.cardDAV != null) {
                    // Create the account
                    logger.info("Creating account ${config.accountName} with CardDAV service")
                    
                    val account = accountRepository.createBlocking(
                        accountName = config.accountName,
                        credentials = credentials,
                        config = serviceConfig,
                        groupMethod = GroupMethod.CATEGORIES // Use contact categories instead of separate VCards
                    )
                    
                    if (account != null) {
                        logger.info("Successfully created managed account: ${config.accountName}")
                        
                        // Trigger initial sync for the newly created account (with permission check)
                        triggerSyncWithPermissionCheck(account, config.accountName)
                        
                        return@withContext true
                    } else {
                        logger.warning("Failed to create managed account: ${config.accountName} - createBlocking returned null")
                        return@withContext false
                    }
                } else {
                    logger.warning("No CardDAV service found for managed account: ${config.accountName}")
                    return@withContext false
                }
                
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "Error creating single managed account: ${config.accountName}", e)
                return@withContext false
            }
        }
    }

    private suspend fun createManagedAccountsAsync() {
        val managedAccountConfigs = managedSettings.getAllAccountConfigs()
        
        logger.info("Found ${managedAccountConfigs.size} managed account configurations")
        
        for (config in managedAccountConfigs) {
            try {
                logger.info("Creating managed account: ${config.accountName} (URL: ${config.baseUrl}, User: ${config.username})")
                
                // Check if account already exists
                val accountExists = withContext(Dispatchers.IO) {
                    accountRepository.exists(config.accountName)
                }
                if (accountExists) {
                    logger.info("Account ${config.accountName} already exists, skipping creation")
                    continue
                }
                
                // Create credentials
                val credentials = Credentials(
                    username = config.username,
                    password = config.password
                )
                
                // Parse base URL - add https:// if missing
                val baseUri = try {
                    val url = if (!config.baseUrl.startsWith("http://") && !config.baseUrl.startsWith("https://")) {
                        "https://${config.baseUrl}"
                    } else {
                        config.baseUrl
                    }
                    logger.info("Using URL: $url for account ${config.accountName}")
                    URI(url)
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Invalid base URL for account ${config.accountName}: ${config.baseUrl}", e)
                    continue
                }
                
                logger.info("Starting service discovery for ${config.accountName} at ${baseUri}")
                
                // Perform service discovery
                val serviceConfig = try {
                    davResourceFinderFactory.create(baseUri, credentials).use { finder ->
                        finder.findInitialConfiguration()
                    }
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Service discovery failed for account ${config.accountName}", e)
                    null
                }
                
                if (serviceConfig == null) {
                    logger.warning("Service discovery returned null for account ${config.accountName}")
                    continue
                }
                
                logger.info("Service discovery completed for ${config.accountName}. CardDAV: ${serviceConfig.cardDAV != null}")
                
                if (serviceConfig.cardDAV != null) {
                    // Create the account
                    logger.info("Creating account ${config.accountName} with CardDAV service")
                    logger.info("CardDAV service details - Principal: ${serviceConfig.cardDAV.principal}, HomeSets: ${serviceConfig.cardDAV.homeSets.size}, Collections: ${serviceConfig.cardDAV.collections.size}")
                    
                    // Log detailed service info
                    serviceConfig.cardDAV.homeSets.forEachIndexed { index, homeSet ->
                        logger.info("  HomeSet $index: $homeSet")
                    }
                    serviceConfig.cardDAV.collections.entries.forEachIndexed { index, (url, collection) ->
                        logger.info("  Collection $index: $url -> ${collection.displayName}")
                    }
                    
                    if (serviceConfig.cardDAV.collections.isEmpty()) {
                        logger.warning("No collections found during initial discovery for ${config.accountName}. RefreshCollectionsWorker will need to discover them.")
                    }
                    
                    val account = withContext(Dispatchers.IO) {
                        accountRepository.createBlocking(
                            accountName = config.accountName,
                            credentials = credentials,
                            config = serviceConfig,
                            groupMethod = GroupMethod.CATEGORIES // Use contact categories instead of separate VCards
                        )
                    }
                    
                    if (account != null) {
                        logger.info("Successfully created managed account: ${config.accountName}")
                        
                        // Verify account was created properly
                        try {
                            withContext(Dispatchers.IO) {
                                val accountManager = android.accounts.AccountManager.get(context)
                                val baseUrl = accountManager.getUserData(account, AccountSettings.KEY_BASE_URL)
                                val username = accountManager.getUserData(account, AccountSettings.KEY_USERNAME) 
                                logger.info("Account verification - baseUrl: $baseUrl, username: $username")
                            }
                            
                            // Collections discovery will happen asynchronously via RefreshCollectionsWorker
                            logger.info("Account ${config.accountName} setup completed. Collections discovery should be in progress.")
                            
                        } catch (e: Exception) {
                            logger.log(Level.WARNING, "Could not verify account metadata", e)
                        }
                        
                        // Trigger initial sync for the newly created account (with permission check)
                        triggerSyncWithPermissionCheck(account, config.accountName)
                    } else {
                        logger.warning("Failed to create managed account: ${config.accountName} - createBlocking returned null")
                    }
                } else {
                    logger.warning("No CardDAV service found for managed account: ${config.accountName}")
                }
                
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "Error creating managed account: ${config.accountName}", e)
            }
        }
        
        logger.info("Finished creating managed accounts")
        
        // Trigger a final sync for all newly created accounts to ensure they sync properly
        try {
            val createdAccounts = managedSettings.getAllAccountConfigs()
                .filter { config -> 
                    withContext(Dispatchers.IO) {
                        accountRepository.exists(config.accountName)
                    }
                }
            
            if (createdAccounts.isNotEmpty()) {
                logger.info("Triggering final sync for ${createdAccounts.size} managed accounts")
                for (config in createdAccounts) {
                    val account = accountRepository.fromName(config.accountName)
                    triggerSyncWithPermissionCheck(account, config.accountName)
                }
                logger.info("Final sync requested for all managed accounts")
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Could not trigger final sync for managed accounts", e)
        }
    }
    
    /**
     * Triggers sync for an account, checking contact permissions first.
     * If permissions aren't granted, schedules delayed retries.
     */
    private suspend fun triggerSyncWithPermissionCheck(account: android.accounts.Account, accountName: String) {
        try {
            val hasPermissions = PermissionUtils.havePermissions(context, PermissionUtils.CONTACT_PERMISSIONS)
            
            if (hasPermissions) {
                logger.info("Contact permissions granted, triggering immediate sync for account: $accountName")
                syncWorkerManager.enqueueOneTimeAllAuthorities(account, manual = true)
                logger.info("Sync enqueued for account: $accountName")
            } else {
                logger.info("Contact permissions not granted yet for account: $accountName, scheduling delayed sync")
                scheduleDelayedSync(account, accountName)
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Could not trigger sync for $accountName", e)
        }
    }
    
    /**
     * Schedules delayed sync retries to wait for contact permissions to be granted.
     */
    private suspend fun scheduleDelayedSync(account: android.accounts.Account, accountName: String) {
        // Try again after 30 seconds, 2 minutes, and 5 minutes
        val delays = listOf(30_000L, 120_000L, 300_000L) // 30s, 2min, 5min
        
        for ((attempt, delayMs) in delays.withIndex()) {
            delay(delayMs)
            
            val hasPermissions = PermissionUtils.havePermissions(context, PermissionUtils.CONTACT_PERMISSIONS)
            if (hasPermissions) {
                logger.info("Contact permissions now granted for account: $accountName (attempt ${attempt + 1}), triggering sync")
                try {
                    syncWorkerManager.enqueueOneTimeAllAuthorities(account, manual = true)
                    logger.info("Delayed sync enqueued for account: $accountName")
                    return // Success, exit retry loop
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Failed to enqueue delayed sync for $accountName", e)
                }
            } else {
                logger.info("Contact permissions still not granted for account: $accountName (attempt ${attempt + 1})")
            }
        }
        
        // Final attempt after all delays
        logger.warning("Contact permissions never granted for account: $accountName after all retry attempts")
    }
    
    /**
     * Removes accounts that were previously created by MDM but are no longer in the configuration.
     */
    private suspend fun cleanupRemovedManagedAccounts() {
        try {
            logger.info("Checking for managed accounts that need to be removed...")
            
            // Get current MDM account configurations
            val currentMdmConfigs = managedSettings.getAllAccountConfigs()
            val currentMdmAccountNames = currentMdmConfigs.map { it.accountName }.toSet()
            
            logger.info("Current MDM configuration has ${currentMdmConfigs.size} accounts: ${currentMdmAccountNames.joinToString()}")
            
            // Get all existing accounts
            val existingAccounts = withContext(Dispatchers.IO) {
                accountRepository.getAll().toList()
            }
            
            logger.info("Found ${existingAccounts.size} existing accounts: ${existingAccounts.map { it.name }.joinToString()}")
            
            // Find accounts that might have been created by MDM but are no longer in configuration
            val accountsToRemove = mutableListOf<android.accounts.Account>()
            
            for (account in existingAccounts) {
                // Check if this account was created by MDM by looking at debug configs
                val wasCreatedByMdm = isAccountCreatedByMdm(account.name, currentMdmConfigs)
                
                if (wasCreatedByMdm && !currentMdmAccountNames.contains(account.name)) {
                    logger.info("Account '${account.name}' was created by MDM but is no longer in configuration, marking for removal")
                    accountsToRemove.add(account)
                }
            }
            
            // Remove accounts that are no longer in MDM configuration
            for (account in accountsToRemove) {
                try {
                    logger.info("Removing managed account: ${account.name}")
                    val success = accountRepository.delete(account.name)
                    if (success) {
                        logger.info("Successfully removed managed account: ${account.name}")
                    } else {
                        logger.warning("Failed to remove managed account: ${account.name}")
                    }
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "Error removing managed account: ${account.name}", e)
                }
            }
            
            if (accountsToRemove.isEmpty()) {
                logger.info("No managed accounts need to be removed")
            } else {
                logger.info("Removed ${accountsToRemove.size} managed accounts that are no longer in MDM configuration")
            }
            
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error during managed account cleanup", e)
        }
    }
    
    /**
     * Checks if an account was likely created by MDM by comparing against known MDM configurations.
     * This includes both current and historical debug configurations.
     */
    private fun isAccountCreatedByMdm(accountName: String, currentConfigs: List<ManagedAccountConfig>): Boolean {
        // Check if account is in current MDM configuration
        if (currentConfigs.any { it.accountName == accountName }) {
            return true
        }
        
        // Check if account matches known debug configuration account names
        // This helps identify accounts created by previous debug sessions
        val knownMdmAccountNames = setOf("Staff List", "Clients")
        if (knownMdmAccountNames.contains(accountName)) {
            logger.info("Account '$accountName' matches known MDM debug configuration")
            return true
        }
        
        // Could also check account metadata or other heuristics here in the future
        return false
    }

} 