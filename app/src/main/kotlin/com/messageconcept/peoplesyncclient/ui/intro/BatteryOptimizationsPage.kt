/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomica.contactzillasync.ui.intro

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import com.atomica.contactzillasync.settings.SettingsManager
import com.atomica.contactzillasync.ui.intro.BatteryOptimizationsPageModel.Companion.HINT_AUTOSTART_PERMISSION
import com.atomica.contactzillasync.ui.intro.BatteryOptimizationsPageModel.Companion.HINT_BATTERY_OPTIMIZATIONS
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class BatteryOptimizationsPage @Inject constructor(
    @ApplicationContext val context: Context,
    val settingsManager: SettingsManager
): IntroPage() {

    override fun getShowPolicy(): ShowPolicy {
        // Don't show - handled by StartupPermissionManager on first run
        return ShowPolicy.DONT_SHOW
    }

    @Composable
    override fun ComposePage() {
        BatteryOptimizationsPageContent()
    }


    @SuppressLint("BatteryLife")
    object IgnoreBatteryOptimizationsContract: ActivityResultContract<String, Unit?>() {
        override fun createIntent(context: Context, input: String): Intent {
            return Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$input")
            )
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Unit? {
            return null
        }
    }

}
