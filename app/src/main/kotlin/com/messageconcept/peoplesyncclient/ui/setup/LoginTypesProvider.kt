/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomica.contactzillasync.ui.setup

import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable

interface LoginTypesProvider {

    data class LoginAction(
        val loginType: LoginType,
        val skipLoginTypePage: Boolean
    )

    val defaultLoginType: LoginType

    /**
     * Which login type to use and whether to skip the login type page. Used for Nextcloud login
     * flow and may be used for other intent started flows.
     */
    fun intentToInitialLoginType(intent: Intent): LoginAction = LoginAction(defaultLoginType, false)

    /** Whether the [LoginTypePage] may be non-interactive. This causes it to be skipped in back navigation. */
    val maybeNonInteractive: Boolean
        get() = false

    @Composable
    fun LoginTypePage(
        snackbarHostState: SnackbarHostState,
        selectedLoginType: LoginType,
        onSelectLoginType: (LoginType) -> Unit,
        setInitialLoginInfo: (LoginInfo) -> Unit,
        onContinue: () -> Unit
    )

}