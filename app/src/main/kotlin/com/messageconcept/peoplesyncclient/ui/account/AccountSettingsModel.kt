/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.account

import android.accounts.Account
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messageconcept.peoplesyncclient.db.AppDatabase
import com.messageconcept.peoplesyncclient.db.Credentials
import com.messageconcept.peoplesyncclient.db.Service
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.settings.ManagedSettings
import com.messageconcept.peoplesyncclient.settings.SettingsManager
import com.messageconcept.peoplesyncclient.sync.SyncDataType
import com.messageconcept.peoplesyncclient.sync.worker.BaseSyncWorker
import com.messageconcept.peoplesyncclient.sync.worker.SyncWorkerManager
import at.bitfire.vcard4android.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel(assistedFactory = AccountSettingsModel.Factory::class)
class AccountSettingsModel @AssistedInject constructor(
    @Assisted val account: Account,
    private val accountSettingsFactory: AccountSettings.Factory,
    @ApplicationContext val context: Context,
    db: AppDatabase,
    private val settings: SettingsManager,
    private val syncWorkerManager: SyncWorkerManager,
    private val managedSettings: ManagedSettings,
): ViewModel(), SettingsManager.OnChangeListener {

    @AssistedFactory
    interface Factory {
        fun create(account: Account): AccountSettingsModel
    }

    // settings
    data class UiState(
        val hasContactsSync: Boolean = false,
        val syncIntervalContacts: Long? = null,
        val hasCalendarsSync: Boolean = false,
        val syncIntervalCalendars: Long? = null,
        val hasTasksSync: Boolean = false,
        val syncIntervalTasks: Long? = null,

        val syncWifiOnly: Boolean = false,
        val syncWifiOnlySSIDs: List<String>? = null,
        val ignoreVpns: Boolean = false,

        val credentials: Credentials = Credentials(),
        val allowCredentialsChange: Boolean = true,

        val timeRangePastDays: Int? = null,
        val defaultAlarmMinBefore: Int? = null,
        val manageCalendarColors: Boolean = false,
        val eventColors: Boolean = false,

        val contactGroupMethod: GroupMethod = GroupMethod.GROUP_VCARDS,

        val allowUsernameChange: Boolean = true,
        val allowPasswordChange: Boolean = true,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val serviceDao = db.serviceDao()

    /**
     * Only acquire account settings on a worker thread!
     */
    private val accountSettings by lazy { accountSettingsFactory.create(account) }

    init {
        settings.addOnChangeListener(this)
        viewModelScope.launch {
            reload()
        }
    }

    override fun onCleared() {
        super.onCleared()
        settings.removeOnChangeListener(this)
    }

    override fun onSettingsChanged() {
        viewModelScope.launch {
            reload()
        }
    }

    private suspend fun reload() = withContext(Dispatchers.Default) {
        val hasContactsSync = serviceDao.getByAccountAndType(account.name, Service.TYPE_CARDDAV) != null

        _uiState.value = UiState(
            hasContactsSync = hasContactsSync,
            syncIntervalContacts = accountSettings.getSyncInterval(SyncDataType.CONTACTS),

            syncWifiOnly = accountSettings.getSyncWifiOnly(),
            syncWifiOnlySSIDs = accountSettings.getSyncWifiOnlySSIDs(),
            ignoreVpns = accountSettings.getIgnoreVpns(),

            credentials = accountSettings.credentials(),
            allowCredentialsChange = accountSettings.changingCredentialsAllowed(),

            contactGroupMethod = accountSettings.getGroupMethod(),

            allowUsernameChange = managedSettings.getUsername().isNullOrEmpty(),
            allowPasswordChange = managedSettings.getPassword().isNullOrEmpty(),
        )
    }

    fun updateContactsSyncInterval(syncInterval: Long) {
        CoroutineScope(Dispatchers.Default).launch {
            accountSettings.setSyncInterval(SyncDataType.CONTACTS, syncInterval.takeUnless { it == -1L })
            reload()
        }
    }

    fun updateSyncWifiOnly(wifiOnly: Boolean) = CoroutineScope(Dispatchers.Default).launch {
        accountSettings.setSyncWiFiOnly(wifiOnly)
        reload()
    }

    fun updateSyncWifiOnlySSIDs(ssids: List<String>?) = CoroutineScope(Dispatchers.Default).launch {
        accountSettings.setSyncWifiOnlySSIDs(ssids)
        reload()
    }

    fun updateIgnoreVpns(ignoreVpns: Boolean) = CoroutineScope(Dispatchers.Default).launch {
        accountSettings.setIgnoreVpns(ignoreVpns)
        reload()
    }

    fun updateCredentials(credentials: Credentials) = CoroutineScope(Dispatchers.Default).launch {
        accountSettings.credentials(credentials)
        reload()
    }

    fun updateContactGroupMethod(groupMethod: GroupMethod) = CoroutineScope(Dispatchers.Default).launch {
        accountSettings.setGroupMethod(groupMethod)
        reload()

        resync(SyncDataType.CONTACTS, fullResync = true)
    }

    /**
     * Initiates re-synchronization for given authority.
     *
     * @param dataType      type of data to synchronize
     * @param fullResync    whether sync shall download all events again
     * (_true_: sets [BaseSyncWorker.FULL_RESYNC],
     * _false_: sets [BaseSyncWorker.RESYNC])
     */
    private fun resync(dataType: SyncDataType, fullResync: Boolean) {
        val resync: Int =
            if (fullResync)
                BaseSyncWorker.FULL_RESYNC
            else
                BaseSyncWorker.RESYNC
        syncWorkerManager.enqueueOneTime(account, dataType, resync = resync)
    }

}