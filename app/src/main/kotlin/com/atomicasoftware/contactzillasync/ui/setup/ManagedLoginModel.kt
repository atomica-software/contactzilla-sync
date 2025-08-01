/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.atomicasoftware.contactzillasync.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.atomicasoftware.contactzillasync.db.Credentials
import com.atomicasoftware.contactzillasync.settings.ManagedSettings
import com.atomicasoftware.contactzillasync.util.DavUtils.toURIorNull
import com.atomicasoftware.contactzillasync.util.trimToNull
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel(assistedFactory = ManagedLoginModel.Factory::class)
class ManagedLoginModel @AssistedInject constructor(
    @Assisted val initialLoginInfo: LoginInfo,
    val managedSettings: ManagedSettings,
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(loginInfo: LoginInfo): ManagedLoginModel
    }

    data class UiState(
        val url: String = "",
        val username: String = "",
        val password: String = "",
        var isUsernameManaged: Boolean = false,
        var isPasswordManaged: Boolean = false,
        var baseUrl: String = "",
        var organization: String = "",
    ) {

        val urlWithPrefix =
            if (url.startsWith("http://") || url.startsWith("https://"))
                url
            else
                "https://$url"
        val uri = urlWithPrefix.toURIorNull()

        val canContinue = uri != null && username.isNotEmpty() && password.isNotEmpty()

        fun asLoginInfo(): LoginInfo =
            LoginInfo(
                baseUri = uri,
                credentials = Credentials(
                    username = username.trimToNull(),
                    password = password.trimToNull(),
                    baseUrl = baseUrl.trimToNull()
                )
            )

    }

    var uiState by mutableStateOf(UiState())
        private set

    init {
        uiState = UiState(
            url = initialLoginInfo.baseUri?.toString()?.removePrefix("https://") ?: "",
            username = initialLoginInfo.credentials?.username ?: "",
            password = initialLoginInfo.credentials?.password ?: "",
            isUsernameManaged = !managedSettings.getEmail().isNullOrEmpty(),
            isPasswordManaged = !managedSettings.getPassword().isNullOrEmpty(),
            baseUrl = managedSettings.getEmail()?.let { email ->
                val domain = email.substringAfter("@")
                val usernameFromEmail = email.substringBefore("@")
                "https://dav.$domain/addressbooks/$usernameFromEmail/"
            } ?: "",
            organization = managedSettings.getOrganization() ?: "",
        )
    }

    fun setUsername(username: String) {
        uiState = uiState.copy(username = username)
    }

    fun setPassword(password: String) {
        uiState = uiState.copy(password = password)
    }

}