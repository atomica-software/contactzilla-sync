/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomica.contactzillasync.di

import com.atomica.contactzillasync.ui.intro.OseIntroPageFactory

import com.atomica.contactzillasync.ui.AboutActivity
import com.atomica.contactzillasync.ui.AccountsDrawerHandler
import com.atomica.contactzillasync.ui.OpenSourceLicenseInfoProvider
import com.atomica.contactzillasync.ui.OseAccountsDrawerHandler
import com.atomica.contactzillasync.ui.intro.IntroPageFactory
import com.atomica.contactzillasync.ui.setup.LoginTypesProvider
import com.atomica.contactzillasync.ui.setup.StandardLoginTypesProvider
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