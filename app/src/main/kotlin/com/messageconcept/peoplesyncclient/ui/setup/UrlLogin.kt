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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Folder
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

object UrlLogin : LoginType {

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
        val model: UrlLoginModel = hiltViewModel(
            creationCallback = { factory: UrlLoginModel.Factory ->
                factory.create(loginInfo = initialLoginInfo)
            }
        )

        val uiState = model.uiState
        UrlLoginScreen(
            url = uiState.url,
            onSetUrl = model::setUrl,
            username = uiState.username,
            onSetUsername = model::setUsername,
            password = uiState.password,
            onSetPassword = model::setPassword,
            canContinue = uiState.canContinue,
            onLogin = {
                if (uiState.canContinue)
                    onLogin(uiState.asLoginInfo())
            }
        )
    }

}

@Composable
fun UrlLoginScreen(
    url: String,
    onSetUrl: (String) -> Unit = {},
    username: String,
    onSetUsername: (String) -> Unit = {},
    password: String,
    onSetPassword: (String) -> Unit = {},
    canContinue: Boolean,
    onLogin: () -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    Assistant(
        nextLabel = stringResource(R.string.login_login),
        nextEnabled = canContinue,
        onNext = onLogin
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                stringResource(R.string.login_type_url),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            OutlinedTextField(
                value = url,
                onValueChange = onSetUrl,
                label = { Text(stringResource(R.string.login_base_url)) },
                placeholder = { Text("dav.example.com/path") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Folder, null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            OutlinedTextField(
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
                modifier = Modifier.fillMaxWidth()
            )

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
                    onDone = { onLogin() }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
@Preview
fun UrlLoginScreen_Preview() {
    UrlLoginScreen(
        url = "https://example.com",
        username = "user",
        password = "",
        canContinue = false
    )
}