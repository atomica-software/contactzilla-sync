/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowedDomainsTest {

    @Test
    fun `production email domains are accepted`() {
        assertTrue(AllowedDomains.isValidEmailDomain("user@contactzilla.app"))
        assertTrue(AllowedDomains.isValidEmailDomain("user@CONTACTZILLA.US"))
    }

    @Test
    fun `localhost test email domain is accepted in debug builds`() {
        // Unit tests run with debug BuildConfig
        assertTrue(AllowedDomains.isValidEmailDomain("user@localhost.test"))
        assertTrue(AllowedDomains.isValidEmailDomain("user@dav.localhost.test"))
    }

    @Test
    fun `email subdomains are rejected`() {
        assertFalse(AllowedDomains.isValidEmailDomain("user@subdomain.contactzilla.app"))
        assertFalse(AllowedDomains.isValidEmailDomain("user@contactzilla.app.evil.com"))
    }

    @Test
    fun `production qr url hosts are accepted`() {
        assertTrue(AllowedDomains.isValidQrUrlHost("contactzilla.app"))
        assertTrue(AllowedDomains.isValidQrUrlHost("dav.contactzilla.app"))
        assertTrue(AllowedDomains.isValidQrUrlHost("contactzilla.us"))
        assertTrue(AllowedDomains.isValidQrUrlHost("dav.contactzilla.us"))
    }

    @Test
    fun `localhost test qr url hosts are accepted in debug builds`() {
        assertTrue(AllowedDomains.isValidQrUrlHost("localhost.test"))
        assertTrue(AllowedDomains.isValidQrUrlHost("dav.localhost.test"))
    }

    @Test
    fun `unrelated qr url hosts are rejected`() {
        assertFalse(AllowedDomains.isValidQrUrlHost("example.com"))
        assertFalse(AllowedDomains.isValidQrUrlHost("evil-contactzilla.app"))
    }
}
