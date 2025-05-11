/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "syncstats",
    foreignKeys = [
        ForeignKey(childColumns = arrayOf("collectionId"), entity = Collection::class, parentColumns = arrayOf("id"), onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index("collectionId", "authority", unique = true),
    ]
)
data class SyncStats(
    @PrimaryKey(autoGenerate = true)
    val id: Long,

    val collectionId: Long,
    val authority: String,

    val lastSync: Long
)