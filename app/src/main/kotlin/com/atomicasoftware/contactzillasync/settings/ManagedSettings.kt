/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.atomicasoftware.contactzillasync.settings

import android.accounts.AccountManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED
import android.content.IntentFilter
import android.content.RestrictionsManager
import android.os.Bundle
import com.atomicasoftware.contactzillasync.R
import com.atomicasoftware.contactzillasync.settings.AccountSettings.Companion.KEY_BASE_URL
import com.atomicasoftware.contactzillasync.settings.AccountSettings.Companion.KEY_USERNAME
import com.atomicasoftware.contactzillasync.BuildConfig
import com.atomicasoftware.contactzillasync.repository.AccountRepository
import com.atomicasoftware.contactzillasync.sync.account.InvalidAccountException
import com.atomicasoftware.contactzillasync.sync.worker.SyncWorkerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

// Data class to represent a managed account configuration
data class ManagedAccountConfig(
    val email: String,
    val password: String,
    val accountName: String
) {
    // Derive baseUrl from email domain
    val baseUrl: String
        get() {
            val domain = email.substringAfter("@")
            return "https://dav.$domain/addressbooks/${email.substringBefore("@")}/"
        }
        
    // Maintain compatibility with existing code that expects username
    val username: String
        get() = email
}

@Singleton
class ManagedSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    private val syncWorkerManager: SyncWorkerManager,
    private val accountRepository: AccountRepository
)  {

    companion object {
        private const val KEY_LOGIN_EMAIL = "login_email"
        private const val KEY_LOGIN_PASSWORD = "login_password"
        private const val KEY_LOGIN_ACCOUNT_NAME = "login_account_name"
        private const val KEY_ORGANIZATION = "organization"
        private const val KEY_MANAGED_BY = "managed_by"
        
        // Keys for additional accounts - Consistent naming pattern for all accounts
        // Account 1: login_base_url_1, Account 2: login_base_url_2, etc.
        private fun getKeyForAccount(accountIndex: Int, baseKey: String): String {
            return "${baseKey}_$accountIndex"
        }
        
        // Debug mode - uses BuildConfig.DEBUG to automatically enable in debug builds
        private val DEBUG_MODE = BuildConfig.DEBUG
        
        // Debug managed_by value for testing
        private const val DEBUG_MANAGED_BY = "Acme Corporation IT Department"
        
        // Test configurations for debug mode (now using email format)
        private val debugConfigs = mapOf(
            1 to ManagedAccountConfig(
                email = "honorableswanobey@contactzilla.app",
                password = "SilentExceptionalHawk4=#8+?!",
                accountName = "Staff List"
            ),
            2 to ManagedAccountConfig(
                email = "flawlesshyenaecho@contactzilla.app",
                password = "PhilanthropicDivineMoor17$%@+4", 
                accountName = "Clients"
            ),
            3 to ManagedAccountConfig(
                email = "hypnoticmonasterysustain@contactzilla.app",
                password = "MagicDeepOwl9%54$=@", 
                accountName = "BackToTheFuture"
            )
        )
    }

    private val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager

    private var restrictions: Bundle
    
    // Coroutine scope for async operations like account cleanup
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val broadCastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_APPLICATION_RESTRICTIONS_CHANGED -> {
                    logger.info("MDM configuration changed, updating managed accounts")
                    // cache app restrictions to avoid unnecessary disk access
                    restrictions = restrictionsManager.applicationRestrictions
                    
                    // Handle account cleanup and updates asynchronously
                    scope.launch {
                        try {
                            // First clean up accounts that are no longer in configuration
                            cleanupRemovedManagedAccounts()
                            // Then update existing accounts (passwords, etc.)
                            updateAccounts()
                        } catch (e: Exception) {
                            logger.log(Level.WARNING, "Error handling MDM configuration change", e)
                        }
                    }
                }
            }
        }
    }

    @Inject
    lateinit var accountsettingsFactory: AccountSettings.Factory

    init {
        restrictions = restrictionsManager.applicationRestrictions
        context.registerReceiver(broadCastReceiver, IntentFilter(ACTION_APPLICATION_RESTRICTIONS_CHANGED))
    }

    fun getEmail(): String? {
        return restrictions.getString(KEY_LOGIN_EMAIL)
    }

    fun getPassword(): String? {
        return restrictions.getString(KEY_LOGIN_PASSWORD)
    }

    fun getOrganization(): String? {
        return restrictions.getString(KEY_ORGANIZATION)
    }

    fun getManagedBy(): String {
        // Use debug value if debug mode is enabled
        if (DEBUG_MODE) {
            return DEBUG_MANAGED_BY
        }
        
        return restrictions.getString(KEY_MANAGED_BY) ?: "your organization"
    }

    // Get account configuration for a specific account number (starting from 1)
    fun getAccountConfig(accountIndex: Int): ManagedAccountConfig? {
        if (accountIndex < 1) return null
        
        // Use debug configuration if debug mode is enabled
        if (DEBUG_MODE && 1==2) {
            logger.info("Debug mode enabled, checking for debug config $accountIndex")
            val debugConfig = debugConfigs[accountIndex]
            if (debugConfig != null) {
                logger.info("Using debug configuration for account $accountIndex: ${debugConfig.accountName}")
                return debugConfig
            } else {
                logger.info("No debug configuration found for account $accountIndex")
            }
        }
        
        val email = restrictions.getString(getKeyForAccount(accountIndex, KEY_LOGIN_EMAIL))
        val password = restrictions.getString(getKeyForAccount(accountIndex, KEY_LOGIN_PASSWORD))
        val accountName = restrictions.getString(getKeyForAccount(accountIndex, KEY_LOGIN_ACCOUNT_NAME))
        
        // Return null if essential fields are missing
        if (email.isNullOrEmpty() || password.isNullOrEmpty()) {
            logger.info("Account config $accountIndex missing essential fields - email: ${email?.isNotEmpty()}, password: ${password?.isNotEmpty()}")
            return null
        }
        
        return ManagedAccountConfig(
            email = email,
            password = password,
            accountName = accountName ?: email.substringBefore("@") // Fallback to username part of email
        )
    }
    
    // Get all configured accounts (dynamically discovers all accounts)
    fun getAllAccountConfigs(): List<ManagedAccountConfig> {
        val configs = mutableListOf<ManagedAccountConfig>()
        logger.info("Getting all account configs, DEBUG_MODE = $DEBUG_MODE")
        
        var accountIndex = 1
        while (true) {
            val config = getAccountConfig(accountIndex)
            if (config != null) {
                logger.info("Found account config $accountIndex: ${config.accountName} - ${config.email}")
                configs.add(config)
                accountIndex++
            } else {
                logger.info("No account config found for index $accountIndex, stopping discovery")
                break
            }
        }
        
        logger.info("Total account configs found: ${configs.size}")
        return configs
    }
    
    // Check if any managed accounts are configured
    fun hasManagedAccounts(): Boolean {
        val configs = getAllAccountConfigs()
        val result = configs.isNotEmpty()
        logger.info("hasManagedAccounts() - configs.size: ${configs.size}, returning: $result")
        return result
    }

    fun loadNewAccountSettings() {
        val accountManager = AccountManager.get(context)

        accountManager.getAccountsByType(context.getString(R.string.account_type)).forEach { account ->
            val version = accountManager.getUserData(account, AccountSettings.KEY_SETTINGS_VERSION).toInt()
            if (version < 20) {
                logger.info("Triggering account migrations for ${account.name}")
                accountsettingsFactory.create(account)
            }
        }
    }

    fun updateAccounts() {
        val accountManager = AccountManager.get(context)

        for (account in accountManager.getAccountsByType(context.getString(R.string.account_type)))
            try {
                val baseUrl = accountManager.getUserData(account, KEY_BASE_URL)

                // we are only interested in managed accounts and only those have the baseUrl
                // attached to their userData
                if (baseUrl != null) {
                    val username = accountManager.getUserData(account, KEY_USERNAME)
                    val password = accountManager.getPassword(account)

                    val managedEmail = getEmail()
                    val managedPassword = getPassword()
                    // If the password has been deleted/unset, do not update existing accounts
                    // and set an empty password as this is guaranteed to lead to synchronization
                    // failures. The alternative would be to delete the account, but this might
                    // be unexpected, so do nothing instead.
                    if (managedPassword.isNullOrEmpty()) {
                        logger.log(Level.INFO,"${account.name}: Managed login password has been deleted. Doing nothing.")
                        return
                    }
                    // check if email matches username
                    val matchesConfig = (managedEmail != null && managedEmail == username)
                    if (matchesConfig) {
                        if (managedPassword != password) {
                            logger.log(Level.INFO,"${account.name}: Managed login password changed. Updating account settings and requesting sync.")
                            accountManager.setPassword(account, managedPassword)
                            // Request an explicit sync after we changed the account password.
                            // This should also clear any error notifications.
                            syncWorkerManager.enqueueOneTimeAllAuthorities(account, manual = true)
                        } else {
                            // Password is up-to-date
                        }
                    }
                }
            } catch (ignored: InvalidAccountException) {
                // account doesn't exist (anymore)
            }
    }

    fun isManaged(): Boolean {
        return !restrictions.getString(KEY_LOGIN_EMAIL).isNullOrEmpty()
    }
    
    /**
     * Removes accounts that were previously created by MDM but are no longer in the configuration.
     * This is called when MDM configuration changes.
     */
    private suspend fun cleanupRemovedManagedAccounts() {
        try {
            logger.info("Checking for managed accounts that need to be removed due to MDM config change...")
            
            // Get current MDM account configurations
            val currentMdmConfigs = getAllAccountConfigs()
            val currentMdmAccountNames = currentMdmConfigs.map { it.accountName }.toSet()
            
            logger.info("Current MDM configuration has ${currentMdmConfigs.size} accounts: ${currentMdmAccountNames.joinToString()}")
            
            // Get all existing accounts
            val existingAccounts = accountRepository.getAll().toList()
            
            logger.info("Found ${existingAccounts.size} existing accounts: ${existingAccounts.map { it.name }.joinToString()}")
            
            // Find accounts that might have been created by MDM but are no longer in configuration
            val accountsToRemove = mutableListOf<android.accounts.Account>()
            
            for (account in existingAccounts) {
                // Check if this account was created by MDM
                val wasCreatedByMdm = isAccountCreatedByMdm(account.name, currentMdmConfigs)
                
                if (wasCreatedByMdm && !currentMdmAccountNames.contains(account.name)) {
                    logger.info("Account '${account.name}' was created by MDM but is no longer in configuration, marking for removal")
                    accountsToRemove.add(account)
                }
            }
            
            // Remove accounts that are no longer in MDM configuration
            for (account in accountsToRemove) {
                try {
                    logger.info("Removing managed account due to MDM config change: ${account.name}")
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