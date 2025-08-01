/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.resource.contactrow

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.atomicasoftware.contactzillasync.resource.LocalContact
import com.atomicasoftware.contactzillasync.resource.LocalTestAddressBookProvider
import at.bitfire.vcard4android.CachedGroupMembership
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.GroupMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.AfterClass
import org.junit.Assert
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class GroupMembershipHandlerTest {

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var localTestAddressBookProvider: LocalTestAddressBookProvider

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    val account = Account("Test Account", "Test Account Type")

    @Before
    fun inject() {
        hiltRule.inject()
    }


    @Test
    fun testMembership_GroupsAsCategories() {
        localTestAddressBookProvider.provide(account, provider, GroupMethod.CATEGORIES) { addressBookGroupsAsCategories ->
            val addressBookGroupsAsCategoriesGroup = addressBookGroupsAsCategories.findOrCreateGroup("TEST GROUP")

            val contact = Contact()
            val localContact = LocalContact(addressBookGroupsAsCategories, contact, null, null, 0)
            GroupMembershipHandler(localContact).handle(ContentValues().apply {
                put(CachedGroupMembership.GROUP_ID, addressBookGroupsAsCategoriesGroup)
                put(CachedGroupMembership.RAW_CONTACT_ID, -1)
            }, contact)
            assertArrayEquals(arrayOf(addressBookGroupsAsCategoriesGroup), localContact.groupMemberships.toArray())
            assertArrayEquals(arrayOf("TEST GROUP"), contact.categories.toArray())
        }
    }


    @Test
    fun testMembership_GroupsAsVCards() {
        localTestAddressBookProvider.provide(account, provider, GroupMethod.GROUP_VCARDS) { addressBookGroupsAsVCards ->
            val contact = Contact()
            val localContact = LocalContact(addressBookGroupsAsVCards, contact, null, null, 0)
            GroupMembershipHandler(localContact).handle(ContentValues().apply {
                put(CachedGroupMembership.GROUP_ID, 12345)    // because the group name is not queried and put into CATEGORIES, the group doesn't have to really exist
                put(CachedGroupMembership.RAW_CONTACT_ID, -1)
            }, contact)
            assertArrayEquals(arrayOf(12345L), localContact.groupMemberships.toArray())
            assertTrue(contact.categories.isEmpty())
        }
    }


    companion object {

        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)!!

        private lateinit var provider: ContentProviderClient

        @BeforeClass
        @JvmStatic
        fun connect() {
            val context: Context = InstrumentationRegistry.getInstrumentation().context
            provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
            Assert.assertNotNull(provider)
        }

        @AfterClass
        @JvmStatic
        fun disconnect() {
            provider.close()
        }

    }

}