/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messageconcept.peoplesyncclient.Constants
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.settings.Settings
import com.messageconcept.peoplesyncclient.ui.AppSettingsModel.PushDistributorInfo
import com.messageconcept.peoplesyncclient.ui.composable.EditTextInputDialog
import com.messageconcept.peoplesyncclient.ui.composable.MultipleChoiceInputDialog
import com.messageconcept.peoplesyncclient.ui.composable.Setting
import com.messageconcept.peoplesyncclient.ui.composable.SettingsHeader
import com.messageconcept.peoplesyncclient.ui.composable.SwitchSetting
import kotlinx.coroutines.launch

@Composable
fun AppSettingsScreen(
    onNavDebugInfo: () -> Unit,
    onExemptFromBatterySaving: () -> Unit,
    onBatterySavingSettings: () -> Unit,
    onNavPermissionsScreen: () -> Unit,
    onShowNotificationSettings: () -> Unit,
    onNavTasksScreen: () -> Unit,
    onNavUp: () -> Unit,
    model: AppSettingsModel = viewModel()
) {
    AppTheme {
        AppSettingsScreen(
            onNavDebugInfo = onNavDebugInfo,
            verboseLogging = model.verboseLogging().collectAsStateWithLifecycle(false).value,
            onUpdateVerboseLogging = model::updateVerboseLogging,
            batterySavingExempted = model.batterySavingExempted.collectAsStateWithLifecycle().value,
            onExemptFromBatterySaving = onExemptFromBatterySaving,
            onBatterySavingSettings = onBatterySavingSettings,
            onNavUp = onNavUp,

            // Connection
            proxyType = model.proxyType().collectAsStateWithLifecycle(null).value ?: Settings.PROXY_TYPE_NONE,
            onProxyTypeUpdated = model::updateProxyType,
            proxyHostName = model.proxyHostName().collectAsStateWithLifecycle(null).value,
            onProxyHostNameUpdated = model::updateProxyHostName,
            proxyPort = model.proxyPort().collectAsStateWithLifecycle(null).value,
            onProxyPortUpdated = model::updateProxyPort,

            // Security
            distrustSystemCerts = model.distrustSystemCertificates().collectAsStateWithLifecycle(null).value ?: false,
            onDistrustSystemCertsUpdated = model::updateDistrustSystemCertificates,
            onResetCertificates = model::resetCertificates,
            onNavPermissionsScreen = onNavPermissionsScreen,

            // User interface
            onShowNotificationSettings = onShowNotificationSettings,
            theme = model.theme().collectAsStateWithLifecycle(null).value ?: Settings.PREFERRED_THEME_DEFAULT,
            onThemeSelected = model::updateTheme,
            onResetHints = model::resetHints,

            // Integration (Tasks and Push)
            tasksAppName = model.tasksAppName.collectAsStateWithLifecycle(null).value ?: stringResource(R.string.app_settings_tasks_provider_none),
            tasksAppIcon = model.tasksAppIcon.collectAsStateWithLifecycle(null).value,
            pushDistributors = model.pushDistributors.collectAsState().value,
            pushDistributor = model.pushDistributor.collectAsState().value,
            onPushDistributorChange = model::updatePushDistributor,
            onNavTasksScreen = onNavTasksScreen
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("BatteryLife")
@Composable
fun AppSettingsScreen(
    onNavDebugInfo: () -> Unit,
    verboseLogging: Boolean,
    onUpdateVerboseLogging: (Boolean) -> Unit,
    batterySavingExempted: Boolean,
    onExemptFromBatterySaving: () -> Unit,
    onBatterySavingSettings: () -> Unit,

    // AppSettings connection
    proxyType: Int,
    onProxyTypeUpdated: (Int) -> Unit,
    proxyHostName: String?,
    onProxyHostNameUpdated: (String) -> Unit,
    proxyPort: Int?,
    onProxyPortUpdated: (Int) -> Unit,

    // AppSettings security
    distrustSystemCerts: Boolean,
    onDistrustSystemCertsUpdated: (Boolean) -> Unit,
    onResetCertificates: () -> Unit,
    onNavPermissionsScreen: () -> Unit,

    // AppSettings UserInterface
    theme: Int,
    onThemeSelected: (Int) -> Unit,
    onResetHints: () -> Unit,

    // AppSettings Integration
    tasksAppName: String,
    tasksAppIcon: Drawable?,
    pushDistributors: List<PushDistributorInfo>?,
    pushDistributor: String?,
    onPushDistributorChange: (String?) -> Unit,
    onNavTasksScreen: () -> Unit,

    onShowNotificationSettings: () -> Unit,
    onNavUp: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavUp) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.navigate_up))
                    }
                },
                title = { Text(stringResource(R.string.app_settings)) },
                actions = {
                    IconButton(onClick = {
                        val settingsUri = Constants.MANUAL_URL.buildUpon()
                            .appendPath(Constants.MANUAL_PATH_SETTINGS)
                            .fragment(Constants.MANUAL_FRAGMENT_APP_SETTINGS)
                            .build()
                        uriHandler.openUri(settingsUri.toString())
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Help, stringResource(R.string.help))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Column(Modifier.padding(8.dp)) {
                AppSettings_Debugging(
                    onNavDebugInfo = onNavDebugInfo,
                    verboseLogging = verboseLogging,
                    onUpdateVerboseLogging = onUpdateVerboseLogging,
                    batterySavingExempted = batterySavingExempted,
                    onExemptFromBatterySaving = onExemptFromBatterySaving,
                    onBatterySavingSettings = onBatterySavingSettings
                )

                AppSettings_Connection(
                    proxyType = proxyType,
                    onProxyTypeUpdated = onProxyTypeUpdated,
                    proxyHostName = proxyHostName,
                    onProxyHostNameUpdated = onProxyHostNameUpdated,
                    proxyPort = proxyPort,
                    onProxyPortUpdated = onProxyPortUpdated,
                )

                val resetCertificatesSuccessMessage = stringResource(R.string.app_settings_reset_certificates_success)
                AppSettings_Security(
                    distrustSystemCerts = distrustSystemCerts,
                    onDistrustSystemCertsUpdated = onDistrustSystemCertsUpdated,
                    onResetCertificates = {
                        onResetCertificates()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(resetCertificatesSuccessMessage)
                        }
                    },
                    onNavPermissionsScreen = onNavPermissionsScreen
                )

                val resetHintsSuccessMessage = stringResource(R.string.app_settings_reset_hints_success)
                AppSettings_UserInterface(
                    theme = theme,
                    onThemeSelected = onThemeSelected,
                    onResetHints = {
                        onResetHints()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(resetHintsSuccessMessage)
                        }
                    },
                    onShowNotificationSettings = onShowNotificationSettings
                )

                AppSettings_Integration(
                    tasksAppName = tasksAppName,
                    tasksAppIcon = tasksAppIcon,
                    pushDistributors = pushDistributors,
                    pushDistributor = pushDistributor,
                    onPushDistributorChange = onPushDistributorChange,
                    onNavTasksScreen = onNavTasksScreen
                )
            }
        }
    }
}

@Composable
@Preview
fun AppSettingsScreen_Preview() {
    AppTheme {
        AppSettingsScreen(
            onNavDebugInfo = {},
            verboseLogging = true,
            batterySavingExempted = true,
            proxyType = 0,
            proxyHostName = "true",
            proxyPort = 0,
            distrustSystemCerts = true,
            theme = 0,
            onUpdateVerboseLogging = {},
            onProxyHostNameUpdated = {},
            onExemptFromBatterySaving = {},
            onBatterySavingSettings = {},
            onShowNotificationSettings = {},
            onNavUp = {},
            onProxyTypeUpdated = {},
            onProxyPortUpdated = {},
            onDistrustSystemCertsUpdated = {},
            onResetCertificates = {},
            onNavPermissionsScreen = {},
            onThemeSelected = {},
            onResetHints = {},
            tasksAppName = "No tasks app",
            tasksAppIcon = null,
            pushDistributors = null,
            pushDistributor = null,
            onPushDistributorChange = {},
            onNavTasksScreen = {}
        )
    }
}

@Composable
fun AppSettings_Debugging(
    onNavDebugInfo: () -> Unit,
    verboseLogging: Boolean,
    onUpdateVerboseLogging: (Boolean) -> Unit,
    batterySavingExempted: Boolean,
    onExemptFromBatterySaving: () -> Unit,
    onBatterySavingSettings: () -> Unit
) {
    SettingsHeader {
        Text(stringResource(R.string.app_settings_debug))
    }

    Setting(
        icon = Icons.Default.BugReport,
        name = stringResource(R.string.app_settings_show_debug_info),
        summary = stringResource(R.string.app_settings_show_debug_info_details)
    ) {
        onNavDebugInfo()
    }

    SwitchSetting(
        icon = Icons.Default.Adb,
        checked = verboseLogging,
        name = stringResource(R.string.app_settings_logging),
        summaryOn = stringResource(R.string.app_settings_logging_on),
        summaryOff = stringResource(R.string.app_settings_logging_off)
    ) {
        onUpdateVerboseLogging(it)
    }

    SwitchSetting(
        checked = batterySavingExempted,
        icon = Icons.Default.SyncProblem.takeUnless { batterySavingExempted },
        name = stringResource(R.string.app_settings_battery_optimization),
        summaryOn = stringResource(R.string.app_settings_battery_optimization_exempted),
        summaryOff = stringResource(R.string.app_settings_battery_optimization_optimized)
    ) {
        if (batterySavingExempted)
            onBatterySavingSettings()
        else
            onExemptFromBatterySaving()
    }
}

@Composable
fun AppSettings_Connection(
    proxyType: Int,
    onProxyTypeUpdated: (Int) -> Unit = {},
    proxyHostName: String? = null,
    onProxyHostNameUpdated: (String) -> Unit = {},
    proxyPort: Int? = null,
    onProxyPortUpdated: (Int) -> Unit = {}
) {
    SettingsHeader(divider = true) {
        Text(stringResource(R.string.app_settings_connection))
    }

    val proxyTypeNames = stringArrayResource(R.array.app_settings_proxy_types)
    val proxyTypeValues = stringArrayResource(R.array.app_settings_proxy_type_values).map { it.toInt() }
    var showProxyTypeInputDialog by remember { mutableStateOf(false) }
    Setting(
        name = stringResource(R.string.app_settings_proxy),
        summary = proxyTypeNames[proxyTypeValues.indexOf(proxyType)]
    ) {
        showProxyTypeInputDialog = true
    }
    if (showProxyTypeInputDialog)
        MultipleChoiceInputDialog(
            title = stringResource(R.string.app_settings_proxy),
            namesAndValues = proxyTypeNames.zip(proxyTypeValues.map { it.toString() }),
            initialValue = proxyType.toString(),
            onValueSelected = { newValue ->
                onProxyTypeUpdated(newValue.toInt())
            },
            onDismiss = { showProxyTypeInputDialog = false }
        )

    if (proxyType !in listOf(Settings.PROXY_TYPE_SYSTEM, Settings.PROXY_TYPE_NONE)) {
        var showProxyHostNameInputDialog by remember { mutableStateOf(false) }
        Setting(
            name = stringResource(R.string.app_settings_proxy_host),
            summary = proxyHostName
        ) {
            showProxyHostNameInputDialog = true
        }
        if (showProxyHostNameInputDialog)
            EditTextInputDialog(
                title = stringResource(R.string.app_settings_proxy_host),
                initialValue = proxyHostName,
                keyboardType = KeyboardType.Uri,
                onValueEntered = onProxyHostNameUpdated,
                onDismiss = { showProxyHostNameInputDialog = false }
            )

        var showProxyPortInputDialog by remember { mutableStateOf(false) }
        Setting(
            name = stringResource(R.string.app_settings_proxy_port),
            summary = proxyPort?.toString()
        ) {
            showProxyPortInputDialog = true
        }
        if (showProxyPortInputDialog)
            EditTextInputDialog(
                title = stringResource(R.string.app_settings_proxy_port),
                initialValue = proxyPort?.toString(),
                keyboardType = KeyboardType.Number,
                onValueEntered = {
                    try {
                        val newPort = it.toInt()
                        if (newPort in 1..65535)
                            onProxyPortUpdated(newPort)
                    } catch (_: NumberFormatException) {
                        // user entered invalid port number
                    }
                },
                onDismiss = { showProxyPortInputDialog = false }
            )
    }
}

@Composable
fun AppSettings_Security(
    distrustSystemCerts: Boolean,
    onDistrustSystemCertsUpdated: (Boolean) -> Unit,
    onResetCertificates: () -> Unit,
    onNavPermissionsScreen: () -> Unit
) {
    SettingsHeader(divider = true) {
        Text(stringResource(R.string.app_settings_security))
    }

    var showingDistrustWarning by remember { mutableStateOf(false) }
    if (showingDistrustWarning) {
        DistrustSystemCertificatesAlertDialog(
            onDistrustSystemCertsRequested = { onDistrustSystemCertsUpdated(true) },
            onDismissRequested = { showingDistrustWarning = false }
        )
    }

    SwitchSetting(
        checked = distrustSystemCerts,
        name = stringResource(R.string.app_settings_distrust_system_certs),
        summaryOn = stringResource(R.string.app_settings_distrust_system_certs_on),
        summaryOff = stringResource(R.string.app_settings_distrust_system_certs_off)
    ) { checked ->
        if (checked) {
            // Show warning before enabling.
            showingDistrustWarning = true
        } else {
            onDistrustSystemCertsUpdated(false)
        }
    }

    Setting(
        name = stringResource(R.string.app_settings_reset_certificates),
        summary = stringResource(R.string.app_settings_reset_certificates_summary),
        onClick = onResetCertificates
    )

    Setting(
        name = stringResource(R.string.app_settings_security_app_permissions),
        summary = stringResource(R.string.app_settings_security_app_permissions_summary),
        onClick = onNavPermissionsScreen
    )
}

@Composable
fun DistrustSystemCertificatesAlertDialog(
    onDistrustSystemCertsRequested: () -> Unit,
    onDismissRequested: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequested,
        icon = { Icon(Icons.Default.Warning, stringResource(R.string.app_settings_distrust_system_certs)) },
        title = { Text(stringResource(R.string.app_settings_distrust_system_certs)) },
        text = { Text(stringResource(R.string.app_settings_distrust_system_certs_dialog_message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDistrustSystemCertsRequested()
                    onDismissRequested()
                }
            ) { Text(stringResource(R.string.dialog_enable)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequested
            ) { Text(stringResource(R.string.dialog_deny)) }
        },
    )
}

@Preview
@Composable
fun DistrustSystemCertificatesAlertDialog_Preview() {
    AppTheme {
        DistrustSystemCertificatesAlertDialog({}, {})
    }
}

@Composable
fun AppSettings_UserInterface(
    theme: Int,
    onThemeSelected: (Int) -> Unit = {},
    onResetHints: () -> Unit = {},
    onShowNotificationSettings: () -> Unit = {}
) {
    SettingsHeader(divider = true) {
        Text(stringResource(R.string.app_settings_user_interface))
    }

    if (Build.VERSION.SDK_INT >= 26)
        Setting(
            icon = Icons.Default.Notifications,
            name = stringResource(R.string.app_settings_notification_settings),
            summary = stringResource(R.string.app_settings_notification_settings_summary),
            onClick = onShowNotificationSettings
        )

    val themeNames = stringArrayResource(R.array.app_settings_theme_names)
    val themeValues = stringArrayResource(R.array.app_settings_theme_values).map { it.toInt() }
    var showThemeDialog by remember { mutableStateOf(false) }
    val themeValueIdx = themeValues.indexOf(theme).takeIf { it != -1 }
    Setting(
        icon = Icons.Default.InvertColors,
        name = stringResource(R.string.app_settings_theme_title),
        summary = themeValueIdx?.let { themeNames[it] }
    ) {
        showThemeDialog = true
    }
    if (showThemeDialog)
        MultipleChoiceInputDialog(
            title = stringResource(R.string.app_settings_theme_title),
            namesAndValues = themeNames.zip(themeValues.map { it.toString() }),
            initialValue = theme.toString(),
            onValueSelected = {
                onThemeSelected(it.toInt())
            },
            onDismiss = { showThemeDialog = false }
        )

    Setting(
        name = stringResource(R.string.app_settings_reset_hints),
        summary = stringResource(R.string.app_settings_reset_hints_summary),
        onClick = onResetHints
    )
}

@Composable
private fun PushDistributorSelectionDialog(
    pushDistributor: String?,
    onPushDistributorChange: (String?) -> Unit,
    pushDistributors: List<PushDistributorInfo>?,
    onDismissRequested: () -> Unit
) {
    var selectedDistributor by remember { mutableStateOf(pushDistributor) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismissRequested,
        confirmButton = {
            TextButton(
                onClick = {
                    onPushDistributorChange(selectedDistributor)
                    onDismissRequested()
                }
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequested
            ) { Text(stringResource(android.R.string.cancel)) }
        },
        title = {
            Text(stringResource(R.string.app_settings_unifiedpush_choose_distributor))
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                if (pushDistributors.isNullOrEmpty()) item {
                    Text(stringResource(R.string.app_settings_unifiedpush_no_distributor))
                } else item {
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = if (selectedDistributor == null) {
                                    Icons.Default.RadioButtonChecked
                                } else {
                                    Icons.Default.RadioButtonUnchecked
                                },
                                contentDescription = null
                            )
                        },
                        headlineContent = {
                            Text(stringResource(R.string.app_settings_unifiedpush_disable))
                        },
                        modifier = Modifier.clickable {
                            selectedDistributor = null
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        )
                    )
                }

                items(pushDistributors.orEmpty()) { (distributor, name, icon) ->
                    val isSelf = distributor == context.packageName
                    val headline = if (isSelf) stringResource(R.string.app_settings_unifiedpush_distributor_fcm) else name ?: distributor
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = if (selectedDistributor == distributor) {
                                    Icons.Default.RadioButtonChecked
                                } else {
                                    Icons.Default.RadioButtonUnchecked
                                },
                                contentDescription = null
                            )
                        },
                        trailingContent = {
                            if (isSelf)
                                Image(
                                    painter = painterResource(R.drawable.product_logomark_cloud_messaging_full_color),
                                    contentDescription = headline,
                                    modifier = Modifier.size(32.dp)
                                )
                            else
                                icon?.let {
                                    Image(
                                        bitmap = icon.toBitmap().asImageBitmap(),
                                        contentDescription = headline,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                        },
                        headlineContent = {
                            Text(headline)
                        },
                        modifier = Modifier.clickable {
                            selectedDistributor = distributor
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        )
                    )
                }

                item {
                    Text(
                        text = buildAnnotatedString {
                            pushStyle(
                                SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
                            )
                            pushLink(
                                LinkAnnotation.Url(
                                    Constants.MANUAL_URL.buildUpon()
                                        .appendPath(Constants.MANUAL_PATH_WEBDAV_PUSH)
                                        .build().toString()
                                )
                            )
                            append(stringResource(R.string.app_settings_unifiedpush_encrypted))
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    )
}

@Composable
@Preview("No distributors installed", "PushDistributorSelectionDialog")
fun PushDistributorSelectionDialog_Preview_NoDistributors() {
    PushDistributorSelectionDialog(null, {}, null) { }
}

@Composable
@Preview("Push disabled", "PushDistributorSelectionDialog")
fun PushDistributorSelectionDialog_Preview_PushDisabled() {
    val ctx = LocalContext.current
    PushDistributorSelectionDialog(
        null,
        {},
        listOf(
            PushDistributorInfo(
                "com.example.distributor1",
                "Distributor 1",
                AppCompatResources.getDrawable(ctx, R.drawable.ic_launcher_foreground)
            )
        )
    ) { }
}

@Composable
@Preview("Distributor Selected", "PushDistributorSelectionDialog")
fun PushDistributorSelectionDialog_Preview_DistributorSelected() {
    val ctx = LocalContext.current
    PushDistributorSelectionDialog(
        "com.example.distributor1",
        {},
        listOf(
            PushDistributorInfo(
                "com.example.distributor1",
                "Distributor 1",
                AppCompatResources.getDrawable(ctx, R.drawable.ic_launcher_foreground)
            ),
            PushDistributorInfo("com.example.distributor2")
        )
    ) { }
}

@Composable
fun AppSettings_Integration(
    tasksAppName: String,
    tasksAppIcon: Drawable? = null,
    pushDistributors: List<PushDistributorInfo>?,
    pushDistributor: String?,
    onPushDistributorChange: (String?) -> Unit,
    onNavTasksScreen: () -> Unit = {}
) {
    SettingsHeader(divider = true) {
        Text(stringResource(R.string.app_settings_integration))
    }
    Setting(
        name = {
            Text(stringResource(R.string.app_settings_tasks_provider))
        },
        icon = {
           tasksAppIcon?.let {
               Image(tasksAppIcon.toBitmap().asImageBitmap(), tasksAppName)
           }
        },
        summary = tasksAppName,
        onClick = onNavTasksScreen
    )

    var showingDistributorDialog by remember { mutableStateOf(false) }
    if (showingDistributorDialog) {
        PushDistributorSelectionDialog(
            pushDistributor = pushDistributor,
            onPushDistributorChange = onPushDistributorChange,
            pushDistributors = pushDistributors
        ) { showingDistributorDialog = false }
    }

    val pushAppName = pushDistributor?.let {
        pushDistributors?.find { it.packageName == pushDistributor }
    }?.appName
    Setting(
        name = stringResource(R.string.app_settings_unifiedpush),
        summary = if (pushDistributor != null)
            stringResource(R.string.app_settings_unifiedpush_ready, pushAppName ?: pushDistributor)
        else
            stringResource(R.string.app_settings_unifiedpush_no_endpoint),
        onClick = { showingDistributorDialog = true }
    )
}