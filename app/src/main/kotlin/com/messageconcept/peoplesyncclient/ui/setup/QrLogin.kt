/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomica.contactzillasync.ui.setup

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.atomica.contactzillasync.R
import com.atomica.contactzillasync.ui.composable.Assistant
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

object QrLogin : LoginType {

    override val title: Int
        get() = R.string.login_type_qr

    override val helpUrl: Uri?
        get() = null

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    override fun LoginScreen(
        snackbarHostState: SnackbarHostState,
        initialLoginInfo: LoginInfo,
        onLogin: (LoginInfo) -> Unit
    ) {
        val context = LocalContext.current
        val model: QrLoginModel = hiltViewModel(
            creationCallback = { factory: QrLoginModel.Factory ->
                factory.create(loginInfo = initialLoginInfo)
            }
        )

        val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
        var qrScanLaunched by remember { mutableStateOf(false) }

        val scanLauncher = rememberLauncherForActivityResult(
            contract = ScanContract()
        ) { result ->
            if (result.contents != null) {
                // QR code scanned successfully
                model.processQrCode(result.contents, context) { loginInfo ->
                    onLogin(loginInfo)
                }
            } else {
                // Scan cancelled or failed
                model.setError(context.getString(R.string.login_qr_scan_error))
            }
        }

        QrLoginScreen(
            onScanQr = {
                if (cameraPermissionState.status.isGranted) {
                    val scanOptions = ScanOptions().apply {
                        setPrompt(context.getString(R.string.login_qr_scan_instructions))
                        setBeepEnabled(false)
                        setOrientationLocked(false)
                        setDesiredBarcodeFormats(listOf("QR_CODE"))
                    }
                    scanLauncher.launch(scanOptions)
                } else {
                    cameraPermissionState.launchPermissionRequest()
                }
            },
            isLoading = model.uiState.isLoading,
            error = model.uiState.error,
            onDismissError = model::clearError,
            hasPermission = cameraPermissionState.status.isGranted,
            shouldShowRationale = cameraPermissionState.status.shouldShowRationale,
            onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
        )

        // Auto-launch QR scan if permission is granted and we haven't launched yet
        LaunchedEffect(cameraPermissionState.status.isGranted, qrScanLaunched) {
            if (cameraPermissionState.status.isGranted && !qrScanLaunched) {
                qrScanLaunched = true
                val scanOptions = ScanOptions().apply {
                    setPrompt(context.getString(R.string.login_qr_scan_instructions))
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                    setDesiredBarcodeFormats(listOf("QR_CODE"))
                }
                scanLauncher.launch(scanOptions)
            }
        }
    }
}

@Composable
fun QrLoginScreen(
    onScanQr: () -> Unit = {},
    isLoading: Boolean = false,
    error: String? = null,
    onDismissError: () -> Unit = {},
    hasPermission: Boolean = false,
    shouldShowRationale: Boolean = false,
    onRequestPermission: () -> Unit = {}
) {
    Assistant(
        nextLabel = stringResource(R.string.login_continue),
        nextEnabled = false, // QR scanning handles navigation
        onNext = { }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.login_qr_scan_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    textAlign = TextAlign.Center
                )

                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.padding(32.dp)
                )

                Text(
                    stringResource(R.string.login_qr_scan_instructions),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )

                when {
                    !hasPermission -> {
                        Text(
                            if (shouldShowRationale) {
                                "Camera permission is required to scan QR codes. Please grant permission to continue."
                            } else {
                                "Camera permission is required to scan QR codes."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        Button(
                            onClick = onRequestPermission,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Grant Camera Permission")
                        }
                    }
                    
                    isLoading -> {
                        Text(
                            "Processing QR code...",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    else -> {
                        Button(
                            onClick = onScanQr,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                            Text(
                                "  Scan QR Code",
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                if (error != null) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
@Preview
fun QrLoginScreen_Preview() {
    QrLoginScreen(
        hasPermission = true
    )
} 