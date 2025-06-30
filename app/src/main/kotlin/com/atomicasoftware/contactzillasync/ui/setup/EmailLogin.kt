/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.ui.setup

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
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
import androidx.core.text.HtmlCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.atomicasoftware.contactzillasync.Constants
import com.atomicasoftware.contactzillasync.R
import com.atomicasoftware.contactzillasync.ui.UiUtils.toAnnotatedString
import com.atomicasoftware.contactzillasync.ui.composable.Assistant
import com.atomicasoftware.contactzillasync.ui.composable.PasswordTextField

object EmailLogin : LoginType {

    override val title: Int
        get() = R.string.login_type_email

    override val helpUrl: Uri?
        get() = null


    @Composable
    override fun LoginScreen(
        snackbarHostState: SnackbarHostState,
        initialLoginInfo: LoginInfo,
        onLogin: (LoginInfo) -> Unit
    ) {
        val model: EmailLoginModel = hiltViewModel(
            creationCallback = { factory: EmailLoginModel.Factory ->
                factory.create(loginInfo = initialLoginInfo)
            }
        )

        val uiState = model.uiState
        EmailLoginScreen(
            email = uiState.email,
            onSetEmail = model::setEmail,
            password = uiState.password,
            onSetPassword = model::setPassword,
            canContinue = uiState.canContinue,
            showDomainError = uiState.showDomainError,
            showGeneralEmailError = uiState.showGeneralEmailError,
            onLogin = { onLogin(uiState.asLoginInfo()) }
        )
    }

}


@Composable
fun EmailLoginScreen(
    email: String,
    onSetEmail: (String) -> Unit = {},
    password: String,
    onSetPassword: (String) -> Unit = {},
    canContinue: Boolean,
    showDomainError: Boolean = false,
    showGeneralEmailError: Boolean = false,
    onLogin: () -> Unit = {}
) {
    Assistant(
        nextLabel = stringResource(R.string.login_login),
        nextEnabled = canContinue,
        onNext = onLogin
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(
                stringResource(R.string.login_type_email),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            val focusRequester = remember { FocusRequester() }
            OutlinedTextField(
                value = email,
                onValueChange = onSetEmail,
                label = { Text(stringResource(R.string.login_email_address)) },
                isError = showDomainError || showGeneralEmailError,
                supportingText = when {
                    showDomainError -> {
                        { Text(stringResource(R.string.login_email_address_domain_error)) }
                    }
                    showGeneralEmailError -> {
                        { Text(stringResource(R.string.login_email_address_error)) }
                    }
                    else -> null
                },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Email, null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            PasswordTextField(
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
                    onDone = { if (canContinue) onLogin() }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


@Composable
@Preview
fun EmailLoginScreen_Preview() {
    EmailLoginScreen(
        email = "test@example.com",
        password = "",
        canContinue = false,
        showDomainError = true,
        showGeneralEmailError = false
    )
}