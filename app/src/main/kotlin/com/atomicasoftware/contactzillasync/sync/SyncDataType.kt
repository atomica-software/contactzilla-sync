/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.sync

import android.provider.ContactsContract

enum class SyncDataType {

    CONTACTS;


    fun possibleAuthorities(): List<String> =
        when (this) {
            CONTACTS -> listOf(
                ContactsContract.AUTHORITY
            )
        }

    companion object {

        fun fromAuthority(authority: String): SyncDataType {
            return when (authority) {
                ContactsContract.AUTHORITY ->
                    CONTACTS
                else -> throw IllegalArgumentException("Unknown authority: $authority")
            }
        }

    }

}