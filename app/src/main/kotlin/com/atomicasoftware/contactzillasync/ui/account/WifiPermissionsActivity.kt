/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.ui.account

import android.accounts.Account
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.res.stringResource
import androidx.core.app.TaskStackBuilder
import com.atomicasoftware.contactzillasync.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WifiPermissionsActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    private val account by lazy { intent.getParcelableExtra<Account>(EXTRA_ACCOUNT) ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WifiPermissionsScreen(
                backgroundPermissionOptionLabel =
                    if (Build.VERSION.SDK_INT >= 30)
                        packageManager.backgroundPermissionOptionLabel.toString()
                    else
                        stringResource(R.string.wifi_permissions_background_location_permission_label),
                onEnableLocationService = {
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    if (intent.resolveActivity(packageManager) != null)
                        startActivity(intent)
                },
                onNavUp = ::onSupportNavigateUp
            )
        }
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

    override fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.editIntentAt(builder.intentCount - 1)?.putExtra(AccountSettingsActivity.EXTRA_ACCOUNT, account)
    }

}