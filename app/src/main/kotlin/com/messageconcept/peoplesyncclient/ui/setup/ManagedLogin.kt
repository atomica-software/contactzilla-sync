/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.atomicasoftware.contactzillasync.ui.setup

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.atomicasoftware.contactzillasync.R
import com.atomicasoftware.contactzillasync.ui.composable.Assistant
import com.atomicasoftware.contactzillasync.ui.composable.PasswordTextField

object ManagedLogin : LoginType {

    override val title
        get() = R.string.login_type_url

    override val helpUrl: Uri?
        get() = null

    @Composable
    override fun LoginScreen(
        snackbarHostState: SnackbarHostState,
        initialLoginInfo: LoginInfo,
        onLogin: (LoginInfo) -> Unit
    ) {
        val model: ManagedLoginModel = hiltViewModel(
            creationCallback = { factory: ManagedLoginModel.Factory ->
                factory.create(loginInfo = initialLoginInfo)
            }
        )

        val uiState = model.uiState
        ManagedLoginScreen(
            username = uiState.username,
            onSetUsername = model::setUsername,
            password = uiState.password,
            onSetPassword = model::setPassword,
            canContinue = uiState.canContinue,
            onLogin = {
                if (uiState.canContinue)
                    onLogin(uiState.asLoginInfo())
            },
            isUsernameManaged = uiState.isUsernameManaged,
            isPasswordManaged = uiState.isPasswordManaged,
            organization = uiState.organization,
        )
    }

}

@Composable
fun ManagedLoginScreen(
    username: String,
    onSetUsername: (String) -> Unit = {},
    password: String,
    onSetPassword: (String) -> Unit = {},
    canContinue: Boolean,
    onLogin: () -> Unit = {},
    isUsernameManaged: Boolean = false,
    isPasswordManaged: Boolean = false,
    organization: String? = null,
) {
    val focusRequester = remember { FocusRequester() }

    // If username and password are provided, proceed immediately with the next step
    if (isUsernameManaged && isPasswordManaged)
        onLogin()

    Assistant(
        nextLabel = stringResource(R.string.login_login),
        nextEnabled = canContinue,
        onNext = onLogin
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            organization?.let { organization ->
                Text(
                    text = organization,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }

            Text(
                stringResource(R.string.login_type_managed),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            OutlinedTextField(
                enabled = !isUsernameManaged,
                value = username,
                onValueChange = onSetUsername,
                label = { Text(stringResource(R.string.login_user_name)) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.AccountCircle, null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            PasswordTextField(
                enabled = !isPasswordManaged,
                password = password,
                onPasswordChange = onSetPassword,
                labelText = stringResource(R.string.login_password),
                leadingIcon = {
                    Icon(Icons.Default.Password, null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onLogin() }
                ),
                modifier = if (isUsernameManaged)
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                else
                    Modifier.fillMaxWidth()
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
@Preview
fun ManagedLoginScreen_Preview() {
    ManagedLoginScreen(
        username = "user",
        password = "",
        canContinue = false
    )
}