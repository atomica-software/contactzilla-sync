/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.webdav

import com.messageconcept.peoplesyncclient.di.MainDispatcher
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class WebDavMountRepositoryTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @MainDispatcher
    lateinit var mainDispatcher: CoroutineDispatcher

    @Inject
    lateinit var repository: WebDavMountRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    val web = MockWebServer()
    val url = web.url("/")

    @Test
    fun testHasWebDav_NoDavHeader() = runTest(mainDispatcher) {
        web.enqueue(MockResponse().setResponseCode(200))
        assertFalse(repository.hasWebDav(url, null))
    }

    @Test
    fun testHasWebDav_DavClass1() = runTest(mainDispatcher) {
        web.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("DAV: 1"))
        assertTrue(repository.hasWebDav(url, null))
    }

    @Test
    fun testHasWebDav_DavClass2() = runTest(mainDispatcher) {
        web.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("DAV: 1, 2"))
        assertTrue(repository.hasWebDav(url, null))
    }

    @Test
    fun testHasWebDav_DavClass3() = runTest(mainDispatcher) {
        web.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("DAV: 1, 3"))
        assertTrue(repository.hasWebDav(url, null))
    }

}