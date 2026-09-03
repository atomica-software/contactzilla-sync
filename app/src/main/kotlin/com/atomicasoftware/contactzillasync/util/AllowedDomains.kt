/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.util

import com.atomicasoftware.contactzillasync.BuildConfig

object AllowedDomains {

    private val productionEmailDomains = listOf(
        "contactzilla.app",
        "contactzilla.us",
    )

    private val debugEmailDomains = listOf(
        "localhost.test",
        "dav.localhost.test",
    )

    private val productionUrlDomains = listOf(
        "contactzilla.app",
        "contactzilla.us",
    )

    private val debugUrlDomains = listOf(
        "localhost.test",
        "gist.githubusercontent.com",
        "raw.githubusercontent.com",
        "localhost",
        "127.0.0.1",
    )

    fun isValidEmailDomain(email: String): Boolean {
        val domain = email.substringAfter('@', "").lowercase()
        if (domain.isEmpty()) return false

        val allowedDomains = productionEmailDomains +
            if (BuildConfig.DEBUG) debugEmailDomains else emptyList()

        return allowedDomains.any { domain == it }
    }

    fun isValidQrUrlHost(host: String): Boolean {
        val hostLower = host.lowercase()
        val allowedDomains = productionUrlDomains +
            if (BuildConfig.DEBUG) debugUrlDomains else emptyList()

        return allowedDomains.any { allowedDomain ->
            hostLower == allowedDomain || hostLower.endsWith(".$allowedDomain")
        }
    }
}
