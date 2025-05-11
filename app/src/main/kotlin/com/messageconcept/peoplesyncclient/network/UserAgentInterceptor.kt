/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.network

import android.os.Build
import com.messageconcept.peoplesyncclient.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttp
import okhttp3.Response
import java.util.Locale
import java.util.logging.Logger

object UserAgentInterceptor: Interceptor {

    val userAgent = "PeopleSync/${BuildConfig.VERSION_NAME} (dav4jvm; " +
            "okhttp/${OkHttp.VERSION}) Android/${Build.VERSION.RELEASE}"

    init {
        Logger.getGlobal().info("Will set User-Agent: $userAgent")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val locale = Locale.getDefault()
        val request = chain.request().newBuilder()
            .header("User-Agent", userAgent)
            .header("Accept-Language", "${locale.language}-${locale.country}, ${locale.language};q=0.7, *;q=0.5")
            .build()
        return chain.proceed(request)
    }

}