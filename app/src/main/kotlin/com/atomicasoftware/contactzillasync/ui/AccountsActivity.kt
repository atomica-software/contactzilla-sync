/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.atomicasoftware.contactzillasync.settings.ManagedSettings
import com.atomicasoftware.contactzillasync.ui.account.AccountActivity

import com.atomicasoftware.contactzillasync.ui.setup.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


@AndroidEntryPoint
class AccountsActivity: AppCompatActivity() {

    @Inject
    lateinit var accountsDrawerHandler: AccountsDrawerHandler

    @Inject
    lateinit var managedSettings: ManagedSettings

    @Inject
    lateinit var startupPermissionManager: StartupPermissionManager




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup startup permission handling
        startupPermissionManager.setupPermissionLaunchers(this)
        
        // Check and request permissions on startup
        startupPermissionManager.checkAndRequestPermissions(this)

        // handle "Sync all" intent from launcher shortcut
        val syncAccounts = intent.action == Intent.ACTION_SYNC

        setContent {
            AccountsScreen(
                initialSyncAccounts = syncAccounts,
                onShowAppIntro = {
                    // Intro screens disabled - StartupPermissionManager handles all permissions directly
                    // This callback should never be triggered since all intro pages return DONT_SHOW
                },
                accountsDrawerHandler = accountsDrawerHandler,
                onAddAccount = {
                    val intent = Intent(this, LoginActivity::class.java)
                    // attach infos from managed settings
                            managedSettings.getEmail()?.let { email ->
            // Generate baseUrl from email
            val domain = email.substringAfter("@")
            val username = email.substringBefore("@")
            val baseUrl = "https://dav.$domain/addressbooks/$username/"
            intent.putExtra(LoginActivity.EXTRA_URL, baseUrl)
            intent.putExtra(LoginActivity.EXTRA_USERNAME, email)
            intent.putExtra(LoginActivity.EXTRA_LOGIN_MANAGED, true)
        }
                    managedSettings.getPassword()?.let {
                        intent.putExtra(LoginActivity.EXTRA_PASSWORD, it)
                    }
                    startActivity(intent)
                },
                onShowAccount = { account ->
                    val intent = Intent(this, AccountActivity::class.java)
                    intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
                    startActivity(intent)
                },
                onManagePermissions = {
                    startActivity(Intent(this, PermissionsActivity::class.java))
                }
            )
        }
    }

}