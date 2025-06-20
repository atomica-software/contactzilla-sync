/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.atomicasoftware.contactzillasync.util.DavUtils.lastSegment
import okhttp3.HttpUrl

@Entity(tableName = "homeset",
        foreignKeys = [
            ForeignKey(entity = Service::class, parentColumns = ["id"], childColumns = ["serviceId"], onDelete = ForeignKey.CASCADE)
        ],
        indices = [
            // index by service; no duplicate URLs per service
            Index("serviceId", "url", unique = true)
        ]
)
data class HomeSet(
    @PrimaryKey(autoGenerate = true)
    val id: Long,

    val serviceId: Long,

    /**
     * Whether this homeset belongs to the [Service.principal] given by [serviceId].
     */
    val personal: Boolean,

    val url: HttpUrl,

    val privBind: Boolean = true,

    val displayName: String? = null
) {

    fun title() = displayName ?: url.lastSegment

}