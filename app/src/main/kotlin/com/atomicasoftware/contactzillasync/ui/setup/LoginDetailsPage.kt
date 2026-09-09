/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atomicasoftware.contactzillasync.R
import com.atomicasoftware.contactzillasync.ui.DebugInfoActivity

@Composable
fun LoginDetailsPage(
    snackbarHostState: SnackbarHostState,
    model: LoginScreenModel = viewModel()
) {
    val uiState = model.loginDetailsUiState
    val detectState = model.detectResourcesUiState

    Box(Modifier.fillMaxSize()) {
        uiState.loginType.LoginScreen(
            snackbarHostState = snackbarHostState,
            initialLoginInfo = uiState.loginInfo,
            onLogin = { loginInfo ->
                model.updateLoginInfo(loginInfo)
                model.navToNextPage()
            }
        )

        if (detectState.loading)
            LoginInProgressOverlay(Modifier.matchParentSize())
    }

    if (model.detectionFailed)
        LoginFailedDialog(
            encountered401 = detectState.encountered401,
            logs = detectState.logs,
            onDismiss = model::dismissDetectionError
        )
}

/**
 * Scrim shown while the entered login details are being verified. It swallows input so that the
 * form underneath can't be edited or submitted again while a login attempt is running.
 */
@Composable
fun LoginInProgressOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = .4f))
            .selectable(
                selected = false,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Text(
                stringResource(R.string.login_querying_server),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

/**
 * Reports a failed login attempt without leaving the login form, so that the user can correct
 * their details instead of typing them again.
 */
@Composable
fun LoginFailedDialog(
    encountered401: Boolean,
    logs: String?,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(
                if (encountered401) R.string.login_auth_failed else R.string.login_no_service
            ))
        },
        text = {
            Text(stringResource(
                if (encountered401) R.string.login_check_credentials else R.string.login_no_service_info
            ))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            if (!logs.isNullOrEmpty())
                TextButton(
                    onClick = {
                        context.startActivity(
                            DebugInfoActivity.IntentBuilder(context)
                                .withLogs(logs)
                                .build()
                        )
                    }
                ) {
                    Text(stringResource(R.string.login_view_logs))
                }
        }
    )
}

@Composable
@Preview
fun LoginFailedDialog_Preview_401() {
    LoginFailedDialog(encountered401 = true, logs = "SOME LOGS")
}

@Composable
@Preview
fun LoginFailedDialog_Preview_NoService() {
    LoginFailedDialog(encountered401 = false, logs = null)
}
