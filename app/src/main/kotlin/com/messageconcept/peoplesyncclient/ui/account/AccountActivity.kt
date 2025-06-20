/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.ui.account

import AccountScreen
import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import com.atomicasoftware.contactzillasync.R
import com.atomicasoftware.contactzillasync.ui.AccountsActivity
import dagger.hilt.android.AndroidEntryPoint
import java.util.logging.Logger
import javax.inject.Inject

@AndroidEntryPoint
class AccountActivity : AppCompatActivity() {

    @Inject
    lateinit var logger: Logger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val account =
            IntentCompat.getParcelableExtra(intent, EXTRA_ACCOUNT, Account::class.java) ?:
            intent.getStringExtra(EXTRA_ACCOUNT)?.let { Account(it, getString(R.string.account_type)) }

        // If account is not passed, log warning and redirect to accounts overview
        if (account == null) {
            logger.warning("AccountActivity requires EXTRA_ACCOUNT")

            // Redirect to accounts overview activity
            val intent = Intent(this, AccountsActivity::class.java).apply {
                // Create a new root activity, do not allow going back.
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            finish()
            return
        }

        setContent {
            AccountScreen(
                account = account,
                onAccountSettings = {
                    val intent = Intent(this, AccountSettingsActivity::class.java)
                    intent.putExtra(AccountSettingsActivity.EXTRA_ACCOUNT, account)
                    startActivity(intent, null)
                },
                onCreateAddressBook = {
                    val intent = Intent(this, CreateAddressBookActivity::class.java)
                    intent.putExtra(CreateAddressBookActivity.EXTRA_ACCOUNT, account)
                    startActivity(intent)
                },
                onCollectionDetails = { collection ->
                    val intent = Intent(this, CollectionActivity::class.java)
                    intent.putExtra(CollectionActivity.EXTRA_ACCOUNT, account)
                    intent.putExtra(CollectionActivity.EXTRA_COLLECTION_ID, collection.id)
                    startActivity(intent, null)
                },
                onNavUp = ::onSupportNavigateUp,
                onFinish = ::finish
            )
        }
    }

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

}