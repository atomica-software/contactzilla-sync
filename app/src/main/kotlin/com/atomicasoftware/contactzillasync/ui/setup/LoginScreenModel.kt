/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.ui.setup

import android.accounts.Account
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atomicasoftware.contactzillasync.repository.AccountRepository
import com.atomicasoftware.contactzillasync.servicedetection.DavResourceFinder
import com.atomicasoftware.contactzillasync.settings.AccountSettings
import com.atomicasoftware.contactzillasync.settings.SettingsManager
import at.bitfire.vcard4android.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.logging.Logger

@HiltViewModel(assistedFactory = LoginScreenModel.Factory::class)
class LoginScreenModel @AssistedInject constructor(
    @Assisted val initialLoginType: LoginType,
    @Assisted val skipLoginTypePage: Boolean,
    @Assisted val initialLoginInfo: LoginInfo,
    private val accountRepository: AccountRepository,
    @ApplicationContext val context: Context,
    private val logger: Logger,
    val loginTypesProvider: LoginTypesProvider,
    private val resourceFinderFactory: DavResourceFinder.Factory,
    settingsManager: SettingsManager
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            initialLoginType: LoginType,
            skipLoginTypePage: Boolean,
            initialLoginInfo: LoginInfo
        ): LoginScreenModel
    }

    enum class Page {
        LoginType,
        LoginDetails,
        AccountDetails
    }

    private val startPage = if (skipLoginTypePage)
        Page.LoginDetails
    else
        Page.LoginType

    var page by mutableStateOf(startPage)
        private set

    var finish by mutableStateOf(false)
        private set


    // navigation events

    fun navToNextPage() {
        // the login flow is already done (for instance, QR code login created the accounts itself)
        if (finish)
            return

        when (page) {
            Page.LoginType -> {
                // continue to login details
                loginDetailsUiState = loginDetailsUiState.copy(
                    loginType = loginTypeUiState.loginType
                )
                page = Page.LoginDetails
            }

            Page.LoginDetails -> {
                // Stay on this page and detect resources in place, so that the entered login
                // details are preserved if detection fails. Only a successful detection advances.
                loginInfo = loginDetailsUiState.loginInfo
                detectResources()
            }

            Page.AccountDetails -> {
                // last page
            }
        }
    }

    /**
     * Called when [detectResources] found a usable configuration. Suggests an account name from
     * the detected configuration and continues to the last page.
     */
    private fun navToAccountDetails() {
        // prefer the address book's own name, so that a manually added account is named the same as
        // one added by QR code or managed configuration
        val addressBookNames = foundConfig?.cardDAV?.addressBookNames.orEmpty()
        val emails = foundConfig?.calDAV?.emails.orEmpty()
        val principalName = foundConfig?.cardDAV?.principal?.pathSegments?.dropLast(1)?.last()
        val initialAccountName = addressBookNames.firstOrNull()
            ?: emails.firstOrNull()
            ?: principalName
            ?: loginInfo.suggestedAccountName
            ?: loginInfo.credentials?.username
            ?: loginInfo.baseUri?.host
            ?: ""
        updateAccountNameAndSuggestions(initialAccountName, (addressBookNames + emails).toSet())
        updateGroupMethod(loginInfo.suggestedGroupMethod)
        page = Page.AccountDetails
    }

    fun navBack() {
        when (page) {
            Page.LoginType ->
                finish = true

            Page.LoginDetails ->
                if (detectResourcesUiState.loading)
                    // a login attempt is running: only abort it, don't leave the page
                    cancelResourceDetection()
                else if (loginTypesProvider.maybeNonInteractive)
                    finish = true
                else
                    page = Page.LoginType

            Page.AccountDetails ->
                page = Page.LoginDetails
        }
    }


    // UI element state – first page: login type

    data class LoginTypeUiState(
        val loginType: LoginType
    )

    var loginTypeUiState by mutableStateOf(LoginTypeUiState(loginType = initialLoginType))
        private set

    fun selectLoginType(loginType: LoginType) {
        loginTypeUiState = loginTypeUiState.copy(loginType = loginType)
        loginDetailsUiState = loginDetailsUiState.copy(loginType = loginType)
    }


    // UI element state – second page: login details

    // base URI and credentials
    private var loginInfo: LoginInfo = initialLoginInfo

    data class LoginDetailsUiState(
        val loginType: LoginType,
        val loginInfo: LoginInfo
    )

    var loginDetailsUiState by mutableStateOf(LoginDetailsUiState(
        loginType = initialLoginType,
        loginInfo = loginInfo
    ))
        private set

    fun updateLoginInfo(loginInfo: LoginInfo) {
        loginDetailsUiState = loginDetailsUiState.copy(loginInfo = loginInfo)
        
        // Check if QR code completion is signaled
        if (loginInfo.qrCodeComplete) {
            logger.info("LoginScreenModel: QR code completion detected, finishing login process")
            finish = true
        }
    }


    // resource detection – runs on top of the login details page

    data class DetectResourcesUiState(
        val loading: Boolean = false,
        val foundNothing: Boolean = false,
        val encountered401: Boolean = false,
        val logs: String? = null
    )

    /** Whether detection failed and the error should be shown to the user. */
    val detectionFailed: Boolean
        get() = detectResourcesUiState.foundNothing

    var detectResourcesUiState by mutableStateOf(DetectResourcesUiState())
        private set

    private var foundConfig: DavResourceFinder.Configuration? = null
    private var detectResourcesJob: Job? = null

    private fun detectResources() {
        val baseUri = loginInfo.baseUri
        if (baseUri == null) {
            logger.warning("LoginScreenModel: resource detection requested without a base URI")
            detectResourcesUiState = DetectResourcesUiState(foundNothing = true)
            return
        }

        // discard the result of any previous attempt
        detectResourcesUiState = DetectResourcesUiState(loading = true)

        detectResourcesJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                 runInterruptible {
                     resourceFinderFactory.create(baseUri, loginInfo.credentials).use { finder ->
                         finder.findInitialConfiguration()
                     }
                 }
            }

            if (result.calDAV != null || result.cardDAV != null) {
                foundConfig = result
                detectResourcesUiState = DetectResourcesUiState()
                navToAccountDetails()

            } else {
                foundConfig = null
                detectResourcesUiState = DetectResourcesUiState(
                    loading = false,
                    foundNothing = true,
                    encountered401 = result.encountered401,
                    logs = result.logs
                )
            }
        }
    }

    private fun cancelResourceDetection() {
        detectResourcesJob?.cancel()
        detectResourcesJob = null
        detectResourcesUiState = DetectResourcesUiState()
    }

    /** Dismisses the error of a failed login attempt, returning the user to the login form. */
    fun dismissDetectionError() {
        detectResourcesUiState = DetectResourcesUiState()
    }


    // UI element state – last page: account details

    data class AccountDetailsUiState(
        val accountName: String = "",
        val suggestedAccountNames: Set<String> = emptySet(),
        val accountNameExists: Boolean = false,
        val groupMethod: GroupMethod = GroupMethod.GROUP_VCARDS,
        val groupMethodReadOnly: Boolean = false,
        val creatingAccount: Boolean = false,
        val createdAccount: Account? = null,
        val couldNotCreateAccount: Boolean = false
    ) {
        val showApostropheWarning = accountName.contains('\'') || accountName.contains('"')
    }

    private val forcedGroupMethod = settingsManager
        .getStringFlow(AccountSettings.KEY_CONTACT_GROUP_METHOD)
        .map { groupMethodName ->
            // map group method name to GroupMethod
            if (groupMethodName != null)
                try {
                    GroupMethod.valueOf(groupMethodName)
                } catch (e: IllegalArgumentException) {
                    logger.warning("Invalid forced group method: $groupMethodName")
                    null
                }
            else
                null
        }

    // backing field that is combined with dynamic content for the resulting UI State
    private var _accountDetailsUiState = MutableStateFlow(AccountDetailsUiState())
    val accountDetailsUiState = combine(_accountDetailsUiState, forcedGroupMethod) { uiState, method ->
        // set group type to read-only if group method is forced
        var combinedState = uiState.copy(groupMethodReadOnly = method != null)

        // apply forced group method, if applicable
        if (method != null)
            combinedState = combinedState.copy(groupMethod = method)

        combinedState
    }.stateIn(viewModelScope, SharingStarted.Lazily, _accountDetailsUiState.value)

    fun updateAccountName(accountName: String) {
        _accountDetailsUiState.update { currentState ->
            currentState.copy(
                accountName = accountName,
                accountNameExists = accountRepository.exists(accountName)
            )
        }
    }

    fun updateAccountNameAndSuggestions(accountName: String, suggestions: Set<String>) {
        _accountDetailsUiState.update { currentState ->
            currentState.copy(
                accountName = accountName,
                accountNameExists = accountRepository.exists(accountName),
                suggestedAccountNames = suggestions
            )
        }
    }

    fun updateGroupMethod(groupMethod: GroupMethod) {
        _accountDetailsUiState.update { currentState ->
            currentState.copy(groupMethod = groupMethod)
        }
    }

    fun resetCouldNotCreateAccount() {
        _accountDetailsUiState.update { currentState ->
            currentState.copy(couldNotCreateAccount = false)
        }
    }

    fun createAccount() {
        val config = foundConfig
        if (config == null) {
            logger.warning("LoginScreenModel: account creation requested without a detected configuration")
            _accountDetailsUiState.update { currentState ->
                currentState.copy(couldNotCreateAccount = true)
            }
            return
        }

        _accountDetailsUiState.update { currentState ->
            currentState.copy(creatingAccount = true)
        }

        viewModelScope.launch {
            val account = withContext(Dispatchers.Default) {
                accountRepository.createBlocking(
                    accountDetailsUiState.value.accountName,
                    loginInfo.credentials,
                    config,
                    accountDetailsUiState.value.groupMethod
                )
            }

            _accountDetailsUiState.update { currentState ->
                if (account != null)
                    currentState.copy(createdAccount = account)
                else
                    currentState.copy(
                        creatingAccount = false,
                        couldNotCreateAccount = true
                    )
            }
        }
    }

}