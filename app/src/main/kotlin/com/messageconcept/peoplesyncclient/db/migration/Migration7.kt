/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.db.migration

import androidx.room.migration.Migration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

val Migration7 = Migration(6, 7) { db ->
    db.execSQL("ALTER TABLE homeset ADD COLUMN privBind INTEGER NOT NULL DEFAULT 1")
    db.execSQL("ALTER TABLE homeset ADD COLUMN displayName TEXT DEFAULT NULL")
}

@Module
@InstallIn(SingletonComponent::class)
internal object Migration7Module {
    @Provides @IntoSet
    fun provide(): Migration = Migration7
}