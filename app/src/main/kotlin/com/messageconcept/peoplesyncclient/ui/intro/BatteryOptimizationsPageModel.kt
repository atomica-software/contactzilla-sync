/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomica.contactzillasync.ui.intro

import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atomica.contactzillasync.BuildConfig
import com.atomica.contactzillasync.settings.SettingsManager
import com.atomica.contactzillasync.util.PermissionUtils
import com.atomica.contactzillasync.util.broadcastReceiverFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class BatteryOptimizationsPageModel @Inject constructor(
    @ApplicationContext val context: Context,
    private val settings: SettingsManager
): ViewModel() {

    companion object {

        /**
         * Whether the request for whitelisting from battery optimizations shall be shown.
         * If this setting is true or null/not set, the notice shall be shown. Only if this
         * setting is false, the notice shall not be shown.
         */
        const val HINT_BATTERY_OPTIMIZATIONS = "hint_BatteryOptimizations"

        /**
         * Whether the autostart permission notice shall be shown. If this setting is true
         * or null/not set, the notice shall be shown. Only if this setting is false, the notice
         * shall not be shown.
         *
         * Type: Boolean
         */
        const val HINT_AUTOSTART_PERMISSION = "hint_AutostartPermissions"

        /**
         * List of manufacturers which are known to restrict background processes or otherwise
         * block synchronization.
         *
         * See https://www.davx5.com/faq/synchronization-is-not-run-as-expected for why this is evil.
         * See https://github.com/jaredrummler/AndroidDeviceNames/blob/master/json/ for manufacturer values.
         */
        private val evilManufacturers = arrayOf("asus", "huawei", "lenovo", "letv", "meizu", "nokia",
            "oneplus", "oppo", "samsung", "sony", "vivo", "wiko", "xiaomi", "zte")

        /**
         * Whether the device has been produced by an evil manufacturer.
         *
         * Always true for debug builds (to test the UI).
         *
         * @see evilManufacturers
         */
        val manufacturerWarning = false

        fun isExempted(context: Context) =
            context.getSystemService<PowerManager>()!!.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)
    }

    data class UiState(
        val shouldBeExempted: Boolean = true,
        val isExempted: Boolean = false
    )

    var uiState by mutableStateOf(UiState())
        private set

    val hintBatteryOptimizations = settings.getBooleanFlow(HINT_BATTERY_OPTIMIZATIONS)

    val hintAutostartPermission = settings.getBooleanFlow(HINT_AUTOSTART_PERMISSION)

    init {
        viewModelScope.launch {
            broadcastReceiverFlow(context, IntentFilter(PermissionUtils.ACTION_POWER_SAVE_WHITELIST_CHANGED), immediate = true).collect {
                checkBatteryOptimizations()
            }
        }
    }

    fun checkBatteryOptimizations() {
        val exempted = isExempted(context)
        uiState = uiState.copy(shouldBeExempted = exempted, isExempted = exempted)

        // if PeopleSync is whitelisted, always show a reminder as soon as it's not whitelisted anymore
        if (exempted)
            settings.remove(HINT_BATTERY_OPTIMIZATIONS)
    }

    fun updateShouldBeExempted(value: Boolean) {
        uiState = uiState.copy(shouldBeExempted = value)
    }

    fun updateHintBatteryOptimizations(value: Boolean) {
        settings.putBoolean(HINT_BATTERY_OPTIMIZATIONS, value)
    }

    fun updateHintAutostartPermission(value: Boolean) {
        settings.putBoolean(HINT_AUTOSTART_PERMISSION, value)
    }

}
