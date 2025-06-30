/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.di

import com.atomicasoftware.contactzillasync.log.LogcatHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Singleton

/**
 * Module that provides verbose logging for tests.
 */
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [LoggerModule::class]
)
@Module
class TestLoggerModule {

    @Provides
    @Singleton
    fun logger(): Logger = Logger.getGlobal().apply {
        level = Level.ALL
        addHandler(LogcatHandler())
    }

}