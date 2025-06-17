/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.atomica.contactzillasync.settings

import android.accounts.AccountManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED
import android.content.IntentFilter
import android.content.RestrictionsManager
import android.os.Bundle
import com.atomica.contactzillasync.R
import com.atomica.contactzillasync.settings.AccountSettings.Companion.KEY_BASE_URL
import com.atomica.contactzillasync.settings.AccountSettings.Companion.KEY_USERNAME
import com.atomica.contactzillasync.BuildConfig
import com.atomica.contactzillasync.sync.account.InvalidAccountException
import com.atomica.contactzillasync.sync.worker.SyncWorkerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

// Data class to represent a managed account configuration
data class ManagedAccountConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
    val accountName: String
)

@Singleton
class ManagedSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    private val syncWorkerManager: SyncWorkerManager
)  {

    companion object {
        private const val KEY_LOGIN_BASE_URL = "login_base_url"
        private const val KEY_LOGIN_USER_NAME = "login_user_name"
        private const val KEY_LOGIN_PASSWORD = "login_password"
        private const val KEY_LOGIN_ACCOUNT_NAME = "login_account_name"
        private const val KEY_ORGANIZATION = "organization"
        
        // Maximum number of accounts supported
        const val MAX_ACCOUNTS = 5
        
        // Keys for additional accounts
        private fun getKeyForAccount(accountIndex: Int, baseKey: String): String {
            return if (accountIndex == 1) baseKey else "${baseKey}_$accountIndex"
        }
        
        // Debug mode - uses BuildConfig.DEBUG to automatically enable in debug builds
        private val DEBUG_MODE = BuildConfig.DEBUG
        
                // Test configurations for debug mode  
        private val debugConfigs = mapOf(
            1 to ManagedAccountConfig(
                baseUrl = "dav.contactzilla.app/addressbooks/honorableswanobey/",  // Try parent directory
                username = "honorableswanobey", 
                password = "SilentExceptionalHawk4=#8+?!",
                accountName = "Staff List"
            ),
            2 to ManagedAccountConfig(
                baseUrl = "dav.contactzilla.app/addressbooks/flawlesshyenaecho/",  // Try parent directory
                username = "flawlesshyenaecho",
                password = "PhilanthropicDivineMoor17$%@+4", 
                accountName = "Clients"
            )
        )
    }

    private val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager

    private var restrictions: Bundle

    private val broadCastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_APPLICATION_RESTRICTIONS_CHANGED -> {
                    // cache app restrictions to avoid unnecessary disk access
                    restrictions = restrictionsManager.applicationRestrictions
                    updateAccounts()
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

    fun getBaseUrl(): String? {
        return restrictions.getString(KEY_LOGIN_BASE_URL)
    }

    fun getUsername(): String? {
        return restrictions.getString(KEY_LOGIN_USER_NAME)
    }

    fun getPassword(): String? {
        return restrictions.getString(KEY_LOGIN_PASSWORD)
    }

    fun getOrganization(): String? {
        return restrictions.getString(KEY_ORGANIZATION)
    }

    // Get account configuration for a specific account number (1-5)
    fun getAccountConfig(accountIndex: Int): ManagedAccountConfig? {
        if (accountIndex < 1 || accountIndex > MAX_ACCOUNTS) return null
        
        // Use debug configuration if debug mode is enabled
        if (DEBUG_MODE) {
            logger.info("Debug mode enabled, checking for debug config $accountIndex")
            val debugConfig = debugConfigs[accountIndex]
            if (debugConfig != null) {
                logger.info("Using debug configuration for account $accountIndex: ${debugConfig.accountName}")
                return debugConfig
            } else {
                logger.info("No debug configuration found for account $accountIndex")
            }
        }
        
        val baseUrl = restrictions.getString(getKeyForAccount(accountIndex, KEY_LOGIN_BASE_URL))
        val username = restrictions.getString(getKeyForAccount(accountIndex, KEY_LOGIN_USER_NAME))
        val password = restrictions.getString(getKeyForAccount(accountIndex, KEY_LOGIN_PASSWORD))
        val accountName = restrictions.getString(getKeyForAccount(accountIndex, KEY_LOGIN_ACCOUNT_NAME))
        
        // Return null if essential fields are missing
        if (baseUrl.isNullOrEmpty() || username.isNullOrEmpty() || password.isNullOrEmpty()) {
            logger.info("Account config $accountIndex missing essential fields - baseUrl: ${baseUrl?.isNotEmpty()}, username: ${username?.isNotEmpty()}, password: ${password?.isNotEmpty()}")
            return null
        }
        
        return ManagedAccountConfig(
            baseUrl = baseUrl,
            username = username,
            password = password,
            accountName = accountName ?: username // Fallback to username if account name not provided
        )
    }
    
    // Get all configured accounts
    fun getAllAccountConfigs(): List<ManagedAccountConfig> {
        val configs = mutableListOf<ManagedAccountConfig>()
        logger.info("Getting all account configs, DEBUG_MODE = $DEBUG_MODE")
        
        for (i in 1..MAX_ACCOUNTS) {
            val config = getAccountConfig(i)
            if (config != null) {
                logger.info("Found account config $i: ${config.accountName} - ${config.baseUrl}")
                configs.add(config)
            } else {
                logger.info("No account config found for index $i")
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

                    val managedBaseUrl = getBaseUrl()
                    val managedUsername = getUsername()
                    val managedPassword = getPassword()
                    // If the password has been deleted/unset, do not update existing accounts
                    // and set an empty password as this is guaranteed to lead to synchronization
                    // failures. The alternative would be to delete the account, but this might
                    // be unexpected, so do nothing instead.
                    if (managedPassword.isNullOrEmpty()) {
                        logger.log(Level.INFO,"${account.name}: Managed login password has been deleted. Doing nothing.")
                        return
                    }
                    // check if baseUrl and userName match
                    if (managedBaseUrl == baseUrl && managedUsername == username) {
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
        return !restrictions.getString(KEY_LOGIN_BASE_URL).isNullOrEmpty()
    }
}