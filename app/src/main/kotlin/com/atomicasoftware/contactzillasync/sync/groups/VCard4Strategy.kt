/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.sync.groups

import android.content.ContentUris
import android.provider.ContactsContract
import com.atomicasoftware.contactzillasync.resource.LocalAddressBook
import com.atomicasoftware.contactzillasync.resource.LocalGroup
import com.atomicasoftware.contactzillasync.sync.ContactsSyncManager.Companion.disjunct
import at.bitfire.vcard4android.BatchOperation
import at.bitfire.vcard4android.Contact
import java.io.FileNotFoundException
import java.util.logging.Logger

class VCard4Strategy(val addressBook: LocalAddressBook): ContactGroupStrategy {

    private val logger: Logger
        get() = Logger.getGlobal()
    
    override fun beforeUploadDirty() {
        /* Mark groups with changed members as dirty:
           1. Iterate over all dirty contacts.
           2. Check whether group memberships have changed by comparing group memberships and cached group memberships.
           3. Mark groups which have been added to/removed from the contact as dirty so that they will be uploaded.
           4. Successful upload will reset dirty flag and update cached group memberships.
         */
        val batch = BatchOperation(addressBook.provider!!)
        for (contact in addressBook.findDirtyContacts())
            try {
                logger.fine("Looking for changed group memberships of contact ${contact.fileName}")
                val cachedGroups = contact.getCachedGroupMemberships()
                val currentGroups = contact.getGroupMemberships()
                for (groupID in cachedGroups disjunct currentGroups) {
                    logger.fine("Marking group as dirty: $groupID")
                    batch.enqueue(BatchOperation.CpoBuilder
                            .newUpdate(addressBook.syncAdapterURI(ContentUris.withAppendedId(ContactsContract.Groups.CONTENT_URI, groupID)))
                            .withValue(ContactsContract.Groups.DIRTY, 1))
                }
            } catch(_: FileNotFoundException) {
            }
        batch.commit()
    }

    override fun verifyContactBeforeSaving(contact: Contact) {
    }

    override fun postProcess() {
        LocalGroup.applyPendingMemberships(addressBook)
    }

}