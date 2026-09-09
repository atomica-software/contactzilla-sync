/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.atomicasoftware.contactzillasync.db.Credentials
import com.atomicasoftware.contactzillasync.util.AllowedDomains
import com.atomicasoftware.contactzillasync.util.DavUtils.toURIorNull
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import okhttp3.HttpUrl
import java.net.URI

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
        /**
         * Email address as it is sent to the server. Contactzilla user names are lower-case and the
         * server compares them case-sensitively, so a stray capital (as inserted by keyboards that
         * auto-capitalize the first character) would otherwise cause an HTTP 401.
         */
        val normalizedEmail = email.trim().lowercase()

        val uri = "mailto:$normalizedEmail".toURIorNull()

        val isValidDomain = AllowedDomains.isValidEmailDomain(normalizedEmail)
        
        // Only show validation errors after user has attempted to continue
        val showDomainError = hasAttemptedContinue && email.isNotEmpty() && !isValidDomain
        
        // Show general email error if email is not empty and URI is null (invalid format) 
        val showGeneralEmailError = hasAttemptedContinue && email.isNotEmpty() && uri == null && isValidDomain

        // Allow button to be enabled for any valid email format, domain validation happens on click
        val canContinue = uri != null && password.isNotEmpty()

        /**
         * Address book URL of the entered account.
         *
         * Contactzilla serves every account's address books at a predictable location, so we
         * address them directly instead of going through SRV / `.well-known` discovery. Those
         * discovery endpoints are served by a different backend than the address books themselves,
         * and only the address book backend is authoritative for these credentials. This is the
         * same URL that managed and QR code setup use.
         */
        val baseUri = addressBookUri(normalizedEmail)

        /** Whether the entered details are complete and valid enough to attempt a login. */
        val canLogin = canContinue && isValidDomain && baseUri != null

        fun asLoginInfo(): LoginInfo {
            return LoginInfo(
                baseUri = baseUri,
                credentials = Credentials(
                    username = normalizedEmail,
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

    companion object {

        /**
         * Builds the address book URL for an email address, e.g.
         * `user@contactzilla.app` → `https://dav.contactzilla.app/addressbooks/user/`.
         *
         * @return the URL, or `null` if [email] isn't a usable address
         */
        fun addressBookUri(email: String): URI? {
            val localPart = email.substringBefore('@', "")
            val domain = email.substringAfter('@', "")
            if (localPart.isEmpty() || domain.isEmpty())
                return null

            // debug builds allow "dav.localhost.test", which already points at the DAV host
            val host = if (domain.startsWith("dav.")) domain else "dav.$domain"

            return try {
                HttpUrl.Builder()
                    .scheme("https")
                    .host(host)
                    .addPathSegment("addressbooks")
                    .addPathSegment(localPart)
                    .addPathSegment("")     // trailing slash, it's a collection
                    .build()
                    .toUri()
            } catch (_: IllegalArgumentException) {
                null
            }
        }

    }

}