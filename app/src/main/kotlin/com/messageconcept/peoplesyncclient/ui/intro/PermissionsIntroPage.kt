/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomica.contactzillasync.ui.intro

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atomica.contactzillasync.BuildConfig
import com.atomica.contactzillasync.R
import com.atomica.contactzillasync.ui.composable.CardWithImage
import com.atomica.contactzillasync.util.PermissionUtils
import com.atomica.contactzillasync.util.PermissionUtils.CONTACT_PERMISSIONS
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

class PermissionsIntroPage @Inject constructor(
    @ApplicationContext private val context: Context
): IntroPage() {

    override fun getShowPolicy(): ShowPolicy {
        // Only show for contacts permissions - other permissions handled by StartupPermissionManager
        val hasContacts = PermissionUtils.havePermissions(context, CONTACT_PERMISSIONS)
        
        return if (hasContacts)
            ShowPolicy.DONT_SHOW
        else
            ShowPolicy.SHOW_ALWAYS
    }

    @Composable
    override fun ComposePage() {
        DirectPermissionRequestScreen()
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DirectPermissionRequestScreen() {
    val context = LocalContext.current
    
    // Contact permissions state
    val contactPermissionState = rememberMultiplePermissionsState(
        permissions = CONTACT_PERMISSIONS.toList()
    )
    
    // Notification permission state (Android 13+)
    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else null
    
    var permissionsRequested by remember { mutableStateOf(false) }
    
    // Auto-request permissions when screen loads
    LaunchedEffect(Unit) {
        if (!permissionsRequested) {
            permissionsRequested = true
            
            // Request contact permissions first
            if (!contactPermissionState.allPermissionsGranted) {
                contactPermissionState.launchMultiplePermissionRequest()
            }
            // Then notification permissions
            else if (notificationPermissionState != null && !notificationPermissionState.status.isGranted) {
                notificationPermissionState.launchPermissionRequest()
            }
        }
    }
    
    val allPermissionsGranted = contactPermissionState.allPermissionsGranted && 
        (notificationPermissionState?.status?.isGranted ?: true)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        CardWithImage(
            title = stringResource(R.string.permissions_essential_title),
            message = stringResource(R.string.permissions_essential_text, stringResource(R.string.app_name)),
            image = painterResource(R.drawable.intro_permissions),
            imageContentScale = ContentScale.Fit,
            modifier = Modifier.padding(8.dp)
        ) {
            
            // Contact permissions status
            PermissionStatusText(
                title = stringResource(R.string.permissions_contacts_title),
                granted = contactPermissionState.allPermissionsGranted,
                grantedText = stringResource(R.string.permissions_contacts_status_on),
                notGrantedText = stringResource(R.string.permissions_contacts_status_off)
            )
            
            // Notification permissions status (Android 13+)
            if (notificationPermissionState != null) {
                Spacer(modifier = Modifier.height(8.dp))
                PermissionStatusText(
                    title = stringResource(R.string.permissions_notification_title),
                    granted = notificationPermissionState.status.isGranted,
                    grantedText = stringResource(R.string.permissions_notification_status_on),
                    notGrantedText = stringResource(R.string.permissions_notification_status_off)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            if (!allPermissionsGranted) {
                Button(
                    onClick = {
                        // Request missing permissions
                        if (!contactPermissionState.allPermissionsGranted) {
                            contactPermissionState.launchMultiplePermissionRequest()
                        } else if (notificationPermissionState != null && !notificationPermissionState.status.isGranted) {
                            notificationPermissionState.launchPermissionRequest()
                        }
                    },
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.permissions_grant_essential))
                }
            }
            
            // Background permissions button
            Button(
                onClick = {
                    // Open battery optimization settings
                    try {
                        val intent = Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback to general battery optimization settings
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        } catch (e2: Exception) {
                            Toast.makeText(context, R.string.permissions_background_settings_unavailable, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(stringResource(R.string.permissions_allow_background_running))
            }
            
            // App settings fallback
            OutlinedButton(
                onClick = { PermissionUtils.showAppSettings(context) },
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(stringResource(R.string.permissions_app_settings))
            }
            
            if (allPermissionsGranted) {
                Text(
                    text = stringResource(R.string.permissions_all_granted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
fun PermissionStatusText(
    title: String,
    granted: Boolean,
    grantedText: String,
    notGrantedText: String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = if (granted) grantedText else notGrantedText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}