/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomica.contactzillasync.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.atomica.contactzillasync.BuildConfig
import com.atomica.contactzillasync.R
import com.atomica.contactzillasync.util.PermissionUtils
import com.atomica.contactzillasync.util.PermissionUtils.CONTACT_PERMISSIONS
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartupPermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
) {
    companion object {
        private const val PREFS_NAME = "startup_permissions"
        private const val KEY_FIRST_RUN_COMPLETE = "first_run_complete"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var contactPermissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var notificationPermissionLauncher: ActivityResultLauncher<String>? = null
    private var batteryOptimizationLauncher: ActivityResultLauncher<Intent>? = null

    fun setupPermissionLaunchers(activity: ComponentActivity) {
        // Contact permissions launcher
        contactPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions.values.all { it }
            logger.info("Contact permissions request result: granted=$granted")
            
            if (!granted) {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.permissions_contacts_required, activity.getString(R.string.app_name)),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                // Continue with other permissions if this is first run
                continuePermissionSequence(activity)
            }
        }

        // Notification permission launcher (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher = activity.registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                logger.info("Notification permission request result: granted=$granted")
                // Continue with battery optimization
                continuePermissionSequence(activity)
            }
        }

        // Battery optimization launcher
        batteryOptimizationLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            logger.info("Battery optimization request completed")
            markFirstRunComplete()
        }
    }

    fun checkAndRequestPermissions(activity: ComponentActivity) {
        val isFirstRun = !prefs.getBoolean(KEY_FIRST_RUN_COMPLETE, false)
        
        logger.info("Checking startup permissions - first run: $isFirstRun")

        // Always check contacts permissions first
        if (!hasContactPermissions()) {
            logger.info("Contact permissions missing - requesting")
            requestContactPermissions()
        } else {
            // Continue with the permission sequence
            continuePermissionSequence(activity)
        }
    }

    private fun continuePermissionSequence(activity: ComponentActivity) {
        val isFirstRun = !prefs.getBoolean(KEY_FIRST_RUN_COMPLETE, false)
        
        if (isFirstRun) {
            // Check notifications and background only on first run
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
                logger.info("Notification permission missing on first run - requesting")
                requestNotificationPermission()
            } else if (!hasBatteryOptimizationExemption()) {
                logger.info("Battery optimization not exempted on first run - requesting")
                requestBatteryOptimizationExemption(activity)
            } else {
                // All permissions handled for first run
                markFirstRunComplete()
            }
        }
    }

    private fun hasContactPermissions(): Boolean {
        return PermissionUtils.havePermissions(context, CONTACT_PERMISSIONS)
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionUtils.havePermissions(context, arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        } else {
            true // Not required on older versions
        }
    }

    private fun hasBatteryOptimizationExemption(): Boolean {
        val powerManager = ContextCompat.getSystemService(context, android.os.PowerManager::class.java)
        return powerManager?.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID) == true
    }

    private fun requestContactPermissions() {
        contactPermissionLauncher?.launch(CONTACT_PERMISSIONS)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestBatteryOptimizationExemption(activity: ComponentActivity) {
        try {
            val intent = Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${BuildConfig.APPLICATION_ID}")
            )
            batteryOptimizationLauncher?.launch(intent)
        } catch (e: Exception) {
            logger.warning("Could not launch battery optimization settings: ${e.message}")
            // Mark first run complete even if we can't request battery optimization
            markFirstRunComplete()
        }
    }

    private fun markFirstRunComplete() {
        prefs.edit().putBoolean(KEY_FIRST_RUN_COMPLETE, true).apply()
        logger.info("First run marked as complete")
    }

    fun shouldShowIntroPages(): Boolean {
        // Only show intro if contacts permissions are missing
        return !hasContactPermissions()
    }
} 