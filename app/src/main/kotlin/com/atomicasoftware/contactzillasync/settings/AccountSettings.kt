/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */
package com.atomicasoftware.contactzillasync.settings

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.annotation.WorkerThread
import androidx.core.os.bundleOf
import com.atomicasoftware.contactzillasync.R
import com.atomicasoftware.contactzillasync.db.Credentials
import com.atomicasoftware.contactzillasync.settings.AccountSettings.Companion.CREDENTIALS_LOCK
import com.atomicasoftware.contactzillasync.settings.AccountSettings.Companion.CREDENTIALS_LOCK_AT_LOGIN_AND_SETTINGS
import com.atomicasoftware.contactzillasync.settings.migration.AccountSettingsMigration
import com.atomicasoftware.contactzillasync.sync.AutomaticSyncManager
import com.atomicasoftware.contactzillasync.sync.SyncDataType
import com.atomicasoftware.contactzillasync.sync.account.InvalidAccountException
import com.atomicasoftware.contactzillasync.sync.account.setAndVerifyUserData
import com.atomicasoftware.contactzillasync.util.trimToNull
import at.bitfire.vcard4android.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import net.openid.appauth.AuthState
import java.util.Collections
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Provider

/**
 * Manages settings of an account.
 *
 * **Must not be called from main thread as it uses blocking I/O and may run migrations.**
 *
 * @param account                   account to take settings from
 * @param abortOnMissingMigration   whether to throw an [IllegalArgumentException] when migrations are missing (useful for testing)
 *
 * @throws InvalidAccountException   on construction when the account doesn't exist (anymore)
 * @throws IllegalArgumentException  when the account is not a ContactzillaSync account or migrations are missing and [abortOnMissingMigration] is set
 */
@WorkerThread   
class AccountSettings @AssistedInject constructor(
    @Assisted val account: Account,
    @Assisted val abortOnMissingMigration: Boolean,
    private val automaticSyncManager: AutomaticSyncManager,
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    private val migrations: Map<Int, @JvmSuppressWildcards Provider<AccountSettingsMigration>>,
    private val settingsManager: SettingsManager
) {

    @AssistedFactory
    interface Factory {
        /**
         * **Must not be called on main thread. Throws exceptions!** See [AccountSettings] for details.
         */
        @WorkerThread
        fun create(account: Account, abortOnMissingMigration: Boolean = false): AccountSettings
    }

    init {
        if (Looper.getMainLooper() == Looper.myLooper())
            throw IllegalThreadStateException("AccountSettings may not be used on main thread")
    }

    val accountManager: AccountManager = AccountManager.get(context)
    init {
        val allowedAccountTypes = arrayOf(
            context.getString(R.string.account_type),
            "com.atomicasoftware.contactzillasync.test"      // R.strings.account_type_test in androidTest
        )
        if (!allowedAccountTypes.contains(account.type))
            throw IllegalArgumentException("Invalid account type for AccountSettings(): ${account.type}")

        // synchronize because account migration must only be run one time
        synchronized(currentlyUpdating) {
            if (currentlyUpdating.contains(account))
                logger.warning("AccountSettings created during migration of $account – not running update()")
            else {
                val versionStr = accountManager.getUserData(account, KEY_SETTINGS_VERSION) ?: throw InvalidAccountException(account)
                var version = 0
                try {
                    version = Integer.parseInt(versionStr)
                } catch (e: NumberFormatException) {
                    logger.log(Level.SEVERE, "Invalid account version: $versionStr", e)
                }
                logger.fine("Account ${account.name} has version $version, current version: $CURRENT_VERSION")

                if (version < CURRENT_VERSION) {
                    currentlyUpdating += account
                    try {
                        update(version, abortOnMissingMigration)
                    } finally {
                        currentlyUpdating -= account
                    }
                }
            }
        }
    }


    // authentication settings

    fun credentials() = Credentials(
        accountManager.getUserData(account, KEY_USERNAME),
        accountManager.getPassword(account),

        accountManager.getUserData(account, KEY_CERTIFICATE_ALIAS),

        accountManager.getUserData(account, KEY_AUTH_STATE)?.let { json ->
            AuthState.jsonDeserialize(json)
        }
    )

    fun credentials(credentials: Credentials) {
        // Basic/Digest auth
        accountManager.setAndVerifyUserData(account, KEY_USERNAME, credentials.username)
        accountManager.setPassword(account, credentials.password)

        // client certificate
        accountManager.setAndVerifyUserData(account, KEY_CERTIFICATE_ALIAS, credentials.certificateAlias)

        // OAuth
        accountManager.setAndVerifyUserData(account, KEY_AUTH_STATE, credentials.authState?.jsonSerializeString())
    }

    /**
     * Returns whether users can modify credentials from the account settings screen.
     * Checks the value of [CREDENTIALS_LOCK] to be `0` or not equal to [CREDENTIALS_LOCK_AT_LOGIN_AND_SETTINGS].
     */
    fun changingCredentialsAllowed(): Boolean {
        val credentialsLock = settingsManager.getIntOrNull(CREDENTIALS_LOCK)
        return credentialsLock == null || credentialsLock != CREDENTIALS_LOCK_AT_LOGIN_AND_SETTINGS
    }


    // sync. settings

    /**
     * Gets the currently set sync interval for this account and data type in seconds.
     *
     * @param dataType  data type of desired sync interval
     * @return sync interval in seconds, or `null` if not set (not applicable or only manual sync)
     */
    fun getSyncInterval(dataType: SyncDataType): Long? {
        val key = when (dataType) {
            SyncDataType.CONTACTS -> KEY_SYNC_INTERVAL_ADDRESSBOOKS
        }
        val seconds = accountManager.getUserData(account, key)?.toLong()
        return when (seconds) {
            null -> settingsManager.getLongOrNull(Settings.DEFAULT_SYNC_INTERVAL)   // no setting → default value
            SYNC_INTERVAL_MANUALLY -> null      // manual sync
            else -> seconds
        }
    }

    /**
     * Sets the sync interval for the given data type and updates the automatic sync.
     *
     * @param dataType              data type of the sync interval to set
     * @param seconds               sync interval in seconds; _null_ for no periodic sync
     */
    fun setSyncInterval(dataType: SyncDataType, seconds: Long?) {
        val key = when (dataType) {
            SyncDataType.CONTACTS -> KEY_SYNC_INTERVAL_ADDRESSBOOKS
        }
        val newValue = if (seconds == null) SYNC_INTERVAL_MANUALLY else seconds
        accountManager.setAndVerifyUserData(account, key, newValue.toString())

        automaticSyncManager.updateAutomaticSync(account, dataType)
    }

    fun getSyncWifiOnly() =
        if (settingsManager.containsKey(KEY_WIFI_ONLY))
            settingsManager.getBoolean(KEY_WIFI_ONLY)
        else
            accountManager.getUserData(account, KEY_WIFI_ONLY) != null

    fun setSyncWiFiOnly(wiFiOnly: Boolean) {
        accountManager.setAndVerifyUserData(account, KEY_WIFI_ONLY, if (wiFiOnly) "1" else null)
        automaticSyncManager.updateAutomaticSync(account)
    }

    fun getSyncWifiOnlySSIDs(): List<String>? =
        if (getSyncWifiOnly()) {
            val strSsids = if (settingsManager.containsKey(KEY_WIFI_ONLY_SSIDS))
                settingsManager.getString(KEY_WIFI_ONLY_SSIDS)
            else
                accountManager.getUserData(account, KEY_WIFI_ONLY_SSIDS)
            strSsids?.split(',')
        } else
            null
    fun setSyncWifiOnlySSIDs(ssids: List<String>?) =
        accountManager.setAndVerifyUserData(account, KEY_WIFI_ONLY_SSIDS, ssids?.joinToString(",").trimToNull())

    fun getIgnoreVpns(): Boolean =
        when (accountManager.getUserData(account, KEY_IGNORE_VPNS)) {
            null -> settingsManager.getBoolean(KEY_IGNORE_VPNS)
            "0" -> false
            else -> true
        }

    fun setIgnoreVpns(ignoreVpns: Boolean) =
        accountManager.setAndVerifyUserData(account, KEY_IGNORE_VPNS, if (ignoreVpns) "1" else "0")


    // CardDAV settings

    fun getGroupMethod(): GroupMethod {
        val name = settingsManager.getString(KEY_CONTACT_GROUP_METHOD) ?:
                accountManager.getUserData(account, KEY_CONTACT_GROUP_METHOD)
        if (name != null)
            try {
                return GroupMethod.valueOf(name)
            }
            catch (_: IllegalArgumentException) {
            }
        return GroupMethod.GROUP_VCARDS
    }

    fun setGroupMethod(method: GroupMethod) {
        accountManager.setAndVerifyUserData(account, KEY_CONTACT_GROUP_METHOD, method.name)
    }


    // UI settings

    /**
     * Whether to show only personal collections in the UI
     *
     * @return *true* if only personal collections shall be shown; *false* otherwise
     */
    fun getShowOnlyPersonal(): Boolean = when (settingsManager.getIntOrNull(KEY_SHOW_ONLY_PERSONAL)) {
        0 -> false
        1 -> true
        else /* including -1 */ -> accountManager.getUserData(account, KEY_SHOW_ONLY_PERSONAL) != null
    }

    /**
     * Whether the user shall be able to change the setting (= setting not locked)
     *
     * @return *true* if the setting is locked; *false* otherwise
     */
    fun getShowOnlyPersonalLocked(): Boolean = when (settingsManager.getIntOrNull(KEY_SHOW_ONLY_PERSONAL)) {
        0, 1 -> true
        else /* including -1 */ -> false
    }

    fun setShowOnlyPersonal(showOnlyPersonal: Boolean) {
        accountManager.setAndVerifyUserData(account, KEY_SHOW_ONLY_PERSONAL, if (showOnlyPersonal) "1" else null)
    }


    // update from previous account settings

    private fun update(baseVersion: Int, abortOnMissingMigration: Boolean) {
        for (toVersion in baseVersion+1 ..CURRENT_VERSION) {
            val fromVersion = toVersion - 1
            logger.info("Updating account ${account.name} settings version $fromVersion → $toVersion")

            val migration = migrations[toVersion]
            if (migration == null) {
                logger.severe("No AccountSettings migration $fromVersion → $toVersion")
                if (abortOnMissingMigration)
                    throw IllegalArgumentException("Missing AccountSettings migration $fromVersion → $toVersion")
            } else {
                try {
                    migration.get().migrate(account)

                    logger.info("Account settings version update to $toVersion successful")
                    accountManager.setAndVerifyUserData(account, KEY_SETTINGS_VERSION, toVersion.toString())
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "Couldn't run AccountSettings migration $fromVersion → $toVersion", e)
                }
            }
        }
    }


    companion object {
        const val CURRENT_VERSION = 20
        const val KEY_SETTINGS_VERSION = "version"

        const val KEY_SYNC_INTERVAL_ADDRESSBOOKS = "sync_interval_addressbooks"
        const val KEY_SYNC_INTERVAL_CALENDARS = "sync_interval_calendars"

        /** Stores the tasks sync interval (in seconds) so that it can be set again when the provider is switched */
        const val KEY_SYNC_INTERVAL_TASKS = "sync_interval_tasks"

        const val KEY_USERNAME = "user_name"
        const val KEY_CERTIFICATE_ALIAS = "certificate_alias"

        const val CREDENTIALS_LOCK = "login_credentials_lock"
        const val CREDENTIALS_LOCK_NO_LOCK = 0
        const val CREDENTIALS_LOCK_AT_LOGIN = 1
        const val CREDENTIALS_LOCK_AT_LOGIN_AND_SETTINGS = 2

        /** OAuth [AuthState] (serialized as JSON) */
        const val KEY_AUTH_STATE = "auth_state"

        const val KEY_BASE_URL = "base_url"

        const val KEY_WIFI_ONLY = "wifi_only"               // sync on WiFi only (default: false)
        const val KEY_WIFI_ONLY_SSIDS = "wifi_only_ssids"   // restrict sync to specific WiFi SSIDs
        const val KEY_IGNORE_VPNS = "ignore_vpns"           // ignore vpns at connection detection

        /** Contact group method:
         *null (not existing)*     groups as separate vCards (default);
        "CATEGORIES"              groups are per-contact CATEGORIES
         */
        const val KEY_CONTACT_GROUP_METHOD = "contact_group_method"

        /** UI preference: Show only personal collections
        value = *null* (not existing)   show all collections (default);
        "1"                             show only personal collections */
        const val KEY_SHOW_ONLY_PERSONAL = "show_only_personal"

        internal const val SYNC_INTERVAL_MANUALLY = -1L

        /** Static property to remember which AccountSettings updates/migrations are currently running */
        val currentlyUpdating = Collections.synchronizedSet(mutableSetOf<Account>())

        fun initialUserData(credentials: Credentials?): Bundle {
            val bundle = bundleOf(KEY_SETTINGS_VERSION to CURRENT_VERSION.toString())

            if (credentials != null) {
                if (credentials.username != null)
                    bundle.putString(KEY_USERNAME, credentials.username)

                if (credentials.certificateAlias != null)
                    bundle.putString(KEY_CERTIFICATE_ALIAS, credentials.certificateAlias)

                if (credentials.authState != null)
                    bundle.putString(KEY_AUTH_STATE, credentials.authState.jsonSerializeString())

                if (credentials.baseUrl != null)
                    bundle.putString(KEY_BASE_URL, credentials.baseUrl)
            }

            return bundle
        }

    }

}