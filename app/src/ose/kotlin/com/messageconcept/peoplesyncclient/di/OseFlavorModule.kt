/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.di

import com.atomicasoftware.contactzillasync.ui.intro.OseIntroPageFactory

import com.atomicasoftware.contactzillasync.ui.AboutActivity
import com.atomicasoftware.contactzillasync.ui.AccountsDrawerHandler
import com.atomicasoftware.contactzillasync.ui.OpenSourceLicenseInfoProvider
import com.atomicasoftware.contactzillasync.ui.OseAccountsDrawerHandler
import com.atomicasoftware.contactzillasync.ui.intro.IntroPageFactory
import com.atomicasoftware.contactzillasync.ui.setup.LoginTypesProvider
import com.atomicasoftware.contactzillasync.ui.setup.StandardLoginTypesProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.components.SingletonComponent

interface OseModules {

    @Module
    @InstallIn(ActivityComponent::class)
    interface ForActivities {
        @Binds
        fun accountsDrawerHandler(impl: OseAccountsDrawerHandler): AccountsDrawerHandler

        @Binds
        fun loginTypesProvider(impl: StandardLoginTypesProvider): LoginTypesProvider
    }

    @Module
    @InstallIn(ViewModelComponent::class)
    interface ForViewModels {
        @Binds
        fun appLicenseInfoProvider(impl: OpenSourceLicenseInfoProvider): AboutActivity.AppLicenseInfoProvider

        @Binds
        fun loginTypesProvider(impl: StandardLoginTypesProvider): LoginTypesProvider
    }

    @Module
    @InstallIn(SingletonComponent::class)
    interface Global {
        @Binds
        fun introPageFactory(impl: OseIntroPageFactory): IntroPageFactory
    }

}