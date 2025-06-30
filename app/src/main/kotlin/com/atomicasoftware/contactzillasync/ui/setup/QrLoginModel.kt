/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.ui.setup

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atomicasoftware.contactzillasync.R
import com.atomicasoftware.contactzillasync.repository.AccountRepository
import com.atomicasoftware.contactzillasync.settings.ManagedAccountConfig
import com.atomicasoftware.contactzillasync.startup.ManagedAccountSetup
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.atomicasoftware.contactzillasync.network.HttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.logging.Level
import java.util.logging.Logger
import java.net.URL
import com.atomicasoftware.contactzillasync.BuildConfig

@HiltViewModel(assistedFactory = QrLoginModel.Factory::class)
class QrLoginModel @AssistedInject constructor(
    @Assisted val initialLoginInfo: LoginInfo,
    private val logger: Logger,
    private val httpClientBuilder: HttpClient.Builder,
    private val accountRepository: AccountRepository,
    private val managedAccountSetup: ManagedAccountSetup
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(loginInfo: LoginInfo): QrLoginModel
    }

    data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null
    )

    var uiState by mutableStateOf(UiState())
        private set

    fun setError(message: String) {
        uiState = uiState.copy(error = message, isLoading = false)
    }

    fun clearError() {
        uiState = uiState.copy(error = null)
    }

    /**
     * Validates that the QR code URL is from an allowed domain for security purposes.
     * In production, only contactzilla.app domain is allowed.
     * In debug mode, additional test domains are permitted.
     */
    private fun isValidQrUrl(url: String): Boolean {
        return try {
            val parsedUrl = URL(url)
            val host = parsedUrl.host?.lowercase() ?: return false
            
            // Allowed domains for production
            val allowedDomains = setOf(
                "contactzilla.app",
                "www.contactzilla.app"
            )
            
            // Additional domains allowed in debug mode for testing
            val debugAllowedDomains = if (BuildConfig.DEBUG) {
                setOf(
                    "gist.githubusercontent.com", // For development testing
                    "raw.githubusercontent.com",
                    "localhost",
                    "127.0.0.1"
                )
            } else {
                emptySet()
            }
            
            val allAllowedDomains = allowedDomains + debugAllowedDomains
            
            // Check if host matches any allowed domain or is a subdomain of contactzilla.app
            val isAllowed = allAllowedDomains.any { allowedDomain ->
                host == allowedDomain || host.endsWith(".$allowedDomain")
            }
            
            if (isAllowed) {
                logger.info("QR URL validation passed for domain: $host")
            } else {
                logger.warning("QR URL validation failed for domain: $host (not in allowed list: $allAllowedDomains)")
            }
            
            isAllowed
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Invalid QR URL format: $url", e)
            false
        }
    }

    /**
     * Processes a QR code by downloading the JSON configuration and creating accounts
     */
    fun processQrCode(qrContent: String, context: Context, onSuccess: (LoginInfo) -> Unit) {
        viewModelScope.launch {
            try {
                uiState = uiState.copy(isLoading = true, error = null)
                
                logger.info("Processing QR code: $qrContent")
                
                // Validate QR URL domain for security
                if (!isValidQrUrl(qrContent)) {
                    logger.warning("QR code URL rejected due to invalid domain: $qrContent")
                    setError(context.getString(R.string.login_qr_domain_error))
                    return@launch
                }
                
                // Download JSON configuration from QR code URL
                val jsonConfig = downloadJsonConfig(qrContent)
                if (jsonConfig == null) {
                    setError(context.getString(R.string.login_qr_config_error))
                    return@launch
                }
                
                // Parse the JSON configuration
                val accountConfigs = parseJsonConfig(jsonConfig)
                if (accountConfigs.isEmpty()) {
                    setError(context.getString(R.string.login_qr_config_error))
                    return@launch
                }
                
                logger.info("Found ${accountConfigs.size} accounts in QR configuration")
                
                // Create accounts using the existing MDM infrastructure
                val createdAccounts = createAccountsFromConfig(accountConfigs, context)
                
                if (createdAccounts > 0) {
                    logger.info("Successfully created $createdAccounts accounts from QR code")
                    
                    // Create a dummy LoginInfo to satisfy the interface
                    // The actual account creation was handled above
                    val dummyLoginInfo = LoginInfo()
                    
                    // Show success message and navigate back
                    uiState = uiState.copy(isLoading = false)
                    onSuccess(dummyLoginInfo)
                } else {
                    setError("Failed to create any accounts from QR configuration")
                }
                
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Error processing QR code", e)
                setError(context.getString(R.string.login_qr_config_error))
            }
        }
    }

    /**
     * Downloads JSON configuration from the QR code URL
     */
    private suspend fun downloadJsonConfig(url: String): String? = withContext(Dispatchers.IO) {
        httpClientBuilder.build().use { httpClient ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                httpClient.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        logger.info("Downloaded JSON config: ${body?.take(200)}...")
                        body
                    } else {
                        logger.warning("Failed to download JSON config: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Error downloading JSON config from $url", e)
                null
            }
        }
    }

    /**
     * Parses the JSON configuration and extracts account configs
     */
    private fun parseJsonConfig(jsonString: String): List<ManagedAccountConfig> {
        try {
            val json = JSONObject(jsonString)
            val managedProperties = json.getJSONArray("managedProperty")
            
            // Convert to a map for easier processing
            val configMap = mutableMapOf<String, String>()
            for (i in 0 until managedProperties.length()) {
                val property = managedProperties.getJSONObject(i)
                val key = property.getString("key")
                val value = when {
                    property.has("valueString") -> property.getString("valueString")
                    property.has("valueInteger") -> property.getInt("valueInteger").toString()
                    else -> continue
                }
                configMap[key] = value
                logger.info("QR Config extracted key: '$key' = '$value'")
            }
            
            // Extract account configurations
            val accounts = mutableListOf<ManagedAccountConfig>()
            
            // Check for numbered accounts starting from _1 (matching MDM system)
            var accountIndex = 1
            while (true) {
                val suffix = "_$accountIndex"
                if (extractAccountConfig(configMap, suffix, accounts)) {
                    accountIndex++
                } else {
                    break
                }
            }
            
            logger.info("Parsed ${accounts.size} account configurations from JSON")
            return accounts
            
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error parsing JSON config", e)
            return emptyList()
        }
    }

    /**
     * Extracts a single account configuration from the config map
     */
    private fun extractAccountConfig(
        configMap: Map<String, String>,
        suffix: String,
        accounts: MutableList<ManagedAccountConfig>
    ): Boolean {
        val email = configMap["login_email$suffix"]
        val password = configMap["login_password$suffix"]
        val accountName = configMap["login_account_name$suffix"]
        
        logger.info("QR Config extraction for suffix '$suffix': email='$email', password='${password?.isNotEmpty()}', accountName='$accountName'")
        
        if (!email.isNullOrEmpty() && !password.isNullOrEmpty()) {
            accounts.add(
                ManagedAccountConfig(
                    email = email,
                    password = password,
                    accountName = accountName ?: email.substringBefore("@")
                )
            )
            logger.info("Extracted account config${suffix}: $accountName ($email)")
            return true
        }
        
        return false
    }

    /**
     * Creates accounts from the parsed configuration using existing MDM infrastructure
     */
    private suspend fun createAccountsFromConfig(
        accountConfigs: List<ManagedAccountConfig>,
        context: Context
    ): Int = withContext(Dispatchers.IO) {
        var createdCount = 0
        
        for (config in accountConfigs) {
            try {
                logger.info("Creating account: ${config.accountName}")
                
                // Check if account already exists
                val exists = accountRepository.exists(config.accountName)
                if (exists) {
                    logger.info("Account ${config.accountName} already exists, skipping")
                    continue
                }
                
                // Use the existing account creation logic from ManagedAccountSetup
                val success = managedAccountSetup.createSingleManagedAccount(config)
                if (success) {
                    createdCount++
                    logger.info("Successfully created account: ${config.accountName}")
                } else {
                    logger.warning("Failed to create account: ${config.accountName}")
                }
                
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Error creating account ${config.accountName}", e)
            }
        }
        
        return@withContext createdCount
    }
} 