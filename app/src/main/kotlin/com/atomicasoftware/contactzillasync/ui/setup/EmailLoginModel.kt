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
        val password: String = ""
    ) {
        val uri = "mailto:$email".toURIorNull()
        
        // Validate that email ends with @contactzilla.app
        val isValidDomain = email.endsWith("@contactzilla.app", ignoreCase = true)
        
        // Show domain error if email is not empty and doesn't have valid domain
        val showDomainError = email.isNotEmpty() && !isValidDomain
        
        // Show general email error if email is not empty and URI is null (invalid format)
        val showGeneralEmailError = email.isNotEmpty() && uri == null && isValidDomain

        val canContinue = uri != null && password.isNotEmpty() && isValidDomain

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
        uiState = uiState.copy(email = email)
    }

    fun setPassword(password: String) {
        uiState = uiState.copy(password = password)
    }

}