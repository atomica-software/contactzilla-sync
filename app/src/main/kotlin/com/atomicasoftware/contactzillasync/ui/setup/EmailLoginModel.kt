/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.atomicasoftware.contactzillasync.db.Credentials
import com.atomicasoftware.contactzillasync.util.DavUtils.toURIorNull
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel(assistedFactory = EmailLoginModel.Factory::class)
class EmailLoginModel @AssistedInject constructor(
    @Assisted val initialLoginInfo: LoginInfo
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(loginInfo: LoginInfo): EmailLoginModel
    }

    data class UiState(
        val email: String = "",
        val password: String = "",
        val hasAttemptedContinue: Boolean = false
    ) {
        val uri = "mailto:$email".toURIorNull()
        
        // Validate that email ends with @contactzilla.app
        val isValidDomain = email.endsWith("@contactzilla.app", ignoreCase = true)
        
        // Only show validation errors after user has attempted to continue
        val showDomainError = hasAttemptedContinue && email.isNotEmpty() && !isValidDomain
        
        // Show general email error if email is not empty and URI is null (invalid format) 
        val showGeneralEmailError = hasAttemptedContinue && email.isNotEmpty() && uri == null && isValidDomain

        // Allow button to be enabled for any valid email format, domain validation happens on click
        val canContinue = uri != null && password.isNotEmpty()

        fun asLoginInfo(): LoginInfo {
            return LoginInfo(
                baseUri = uri,
                credentials = Credentials(
                    username = email,
                    password = password
                )
            )
        }
    }

    var uiState by mutableStateOf(UiState())
        private set

    init {
        uiState = uiState.copy(
            email = initialLoginInfo.credentials?.username ?: "",
            password = initialLoginInfo.credentials?.password ?: ""
        )
    }

    fun setEmail(email: String) {
        uiState = uiState.copy(email = email, hasAttemptedContinue = false)
    }

    fun setPassword(password: String) {
        uiState = uiState.copy(password = password)
    }
    
    fun attemptContinue() {
        uiState = uiState.copy(hasAttemptedContinue = true)
    }

}