/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.ui.account

import android.accounts.Account
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.atomicasoftware.contactzillasync.db.HomeSet
import com.atomicasoftware.contactzillasync.repository.DavCollectionRepository
import com.atomicasoftware.contactzillasync.repository.DavHomeSetRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = CreateAddressBookModel.Factory::class)
class CreateAddressBookModel @AssistedInject constructor(
    @Assisted val account: Account,
    private val collectionRepository: DavCollectionRepository,
    homeSetRepository: DavHomeSetRepository
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(account: Account): CreateAddressBookModel
    }

    val addressBookHomeSets = homeSetRepository.getAddressBookHomeSetsFlow(account)


    // UI state

    data class UiState(
        val error: Exception? = null,
        val success: Boolean = false,

        val displayName: String = "",
        val description: String = "",
        val selectedHomeSet: HomeSet? = null,
        val isCreating: Boolean = false
    ) {
        val canCreate = !isCreating && displayName.isNotBlank() && selectedHomeSet != null
    }

    var uiState by mutableStateOf(UiState())
        private set

    fun resetError() {
        uiState = uiState.copy(error = null)
    }

    fun setDisplayName(displayName: String) {
        uiState = uiState.copy(displayName = displayName)
    }

    fun setDescription(description: String) {
        uiState = uiState.copy(description = description)
    }

    fun setHomeSet(homeSet: HomeSet) {
        uiState = uiState.copy(selectedHomeSet = homeSet)
    }


    // actions

    /* Creating collections shouldn't be cancelled when the view is destroyed, otherwise we might
    end up with collections on the server that are not represented in the database/UI. */
    private val createCollectionScope = CoroutineScope(SupervisorJob())

    fun createAddressBook() {
        val homeSet = uiState.selectedHomeSet ?: return
        uiState = uiState.copy(isCreating = true)

        createCollectionScope.launch {
            uiState = try {
                collectionRepository.createAddressBook(
                    account = account,
                    homeSet = homeSet,
                    displayName = uiState.displayName,
                    description = uiState.description
                )

                uiState.copy(isCreating = false, success = true)
            } catch (e: Exception) {
                uiState.copy(isCreating = false, error = e)
            }
        }
    }

}