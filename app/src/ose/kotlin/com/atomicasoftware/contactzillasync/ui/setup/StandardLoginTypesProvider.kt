/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.ui.setup

import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.atomicasoftware.contactzillasync.ui.setup.LoginTypesProvider.LoginAction
import java.util.logging.Logger
import javax.inject.Inject

class StandardLoginTypesProvider @Inject constructor(
    private val logger: Logger
) : LoginTypesProvider {

    companion object {
        val genericLoginTypes = listOf(
            QrLogin,
            EmailLogin
        )
    }

    override val defaultLoginType = QrLogin

    override fun intentToInitialLoginType(intent: Intent): LoginAction =
        intent.data?.normalizeScheme().let { uri ->
            when {
                intent.hasExtra(LoginActivity.EXTRA_LOGIN_MANAGED) ->
                    LoginAction(ManagedLogin, true)
                uri?.scheme == "mailto" ->
                    LoginAction(EmailLogin, true)
                listOf("carddavs", "http", "https").any { uri?.scheme == it } ->
                    LoginAction(EmailLogin, true) // Redirect URL intents to email login
                else -> {
                    logger.warning("Did not understand login intent: $intent")
                    LoginAction(defaultLoginType, false) // Don't skip login type page if intent is unclear
                }
            }
        }

    override val maybeNonInteractive: Boolean
        get() = true

    @Composable
    override fun LoginTypePage(
        snackbarHostState: SnackbarHostState,
        selectedLoginType: LoginType,
        onSelectLoginType: (LoginType) -> Unit,
        setInitialLoginInfo: (LoginInfo) -> Unit,
        onContinue: () -> Unit
    ) {
        StandardLoginTypePage(
            selectedLoginType = selectedLoginType,
            onSelectLoginType = onSelectLoginType,
            setInitialLoginInfo = setInitialLoginInfo,
            onContinue = onContinue
        )
    }

}