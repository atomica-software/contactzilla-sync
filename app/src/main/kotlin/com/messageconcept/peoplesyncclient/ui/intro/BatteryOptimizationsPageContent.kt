/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomica.contactzillasync.ui.intro

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atomica.contactzillasync.BuildConfig
import com.atomica.contactzillasync.Constants
import com.atomica.contactzillasync.Constants.withStatParams
import com.atomica.contactzillasync.R
import com.atomica.contactzillasync.ui.AppTheme
import com.atomica.contactzillasync.ui.UiUtils.isPortrait
import java.util.Locale

@Composable
fun BatteryOptimizationsPageContent(
    model: BatteryOptimizationsPageModel = viewModel()
) {
    // Check battery optimization status when component loads
    LaunchedEffect(Unit) {
        model.checkBatteryOptimizations()
    }
    
    val isExempted = model.uiState.isExempted

    // Auto-request battery optimization exemption when screen loads
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        contract = BatteryOptimizationsPage.IgnoreBatteryOptimizationsContract,
        onResult = { model.checkBatteryOptimizations() }
    )

    LaunchedEffect(isExempted) {
        if (!isExempted) {
            // Automatically request battery optimization exemption
            batteryOptimizationLauncher.launch(BuildConfig.APPLICATION_ID)
        }
    }

    BatteryOptimizationsPageContentUI(
        isExempted = isExempted,
        onRequestExemption = {
            batteryOptimizationLauncher.launch(BuildConfig.APPLICATION_ID)
        }
    )
}

@Composable
fun BatteryOptimizationsPageContentUI(
    isExempted: Boolean = false,
    onRequestExemption: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier.padding(8.dp)
        ) {
            if (isPortrait()) {
                Column {
                    Image(
                        painter = painterResource(R.drawable.intro_regular_sync),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 252.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.intro_battery_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = stringResource(
                        R.string.intro_battery_text_direct,
                        stringResource(R.string.app_name)
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )

                if (isExempted) {
                    Text(
                        text = stringResource(R.string.intro_battery_exempted),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                } else {
                    Button(
                        onClick = onRequestExemption,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(stringResource(R.string.intro_battery_allow_background))
                    }

                    Text(
                        text = stringResource(R.string.intro_battery_manual_instruction),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Manufacturer-specific autostart permissions
                if (BatteryOptimizationsPageModel.manufacturerWarning) {
                    Text(
                        text = stringResource(
                            R.string.intro_autostart_text,
                            stringResource(R.string.app_name),
                            Build.MANUFACTURER.replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            uriHandler.openUri(
                                Constants.HOMEPAGE_URL.buildUpon().withStatParams("intro-autostart").build().toString()
                            )
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.intro_autostart_more_info))
                    }
                }
            }
        }
    }
}

@Composable
@Preview
fun BatteryOptimizationsPageContent_Preview() {
    AppTheme {
        BatteryOptimizationsPageContentUI()
    }
}
