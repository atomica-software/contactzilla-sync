/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */
package com.atomicasoftware.contactzillasync.sync.account

import android.accounts.Account
import android.accounts.AccountManager
import androidx.test.platform.app.InstrumentationRegistry
import com.atomicasoftware.contactzillasync.R
import com.atomicasoftware.contactzillasync.settings.AccountSettings
import org.junit.Assert.assertTrue

object TestAccount {

    private val targetContext by lazy { InstrumentationRegistry.getInstrumentation().targetContext }

    /**
     * Creates a test account, usually in the `Before` setUp of a test.
     *
     * Remove it with [remove].
     */
    fun create(version: Int = AccountSettings.CURRENT_VERSION): Account {
        val accountType = targetContext.getString(R.string.account_type)
        val account = Account("Test Account", accountType)

        val initialData = AccountSettings.initialUserData(null)
        initialData.putString(AccountSettings.KEY_SETTINGS_VERSION, version.toString())
        assertTrue(SystemAccountUtils.createAccount(targetContext, account, initialData))

        return account
    }

    /**
     * Removes a test account, usually in the `@After` tearDown of a test.
     */
    fun remove(account: Account) {
        val am = AccountManager.get(targetContext)
        assertTrue(am.removeAccountExplicitly(account))
    }

    /**
     * Convenience method to create a test account and remove it after executing the block.
     */
    fun provide(version: Int = AccountSettings.CURRENT_VERSION, block: (Account) -> Unit) {
        val account = create(version)
        try {
            block(account)
        } finally {
            remove(account)
        }
    }

}