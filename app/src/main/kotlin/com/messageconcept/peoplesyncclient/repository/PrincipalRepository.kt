/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.repository

import com.atomicasoftware.contactzillasync.db.AppDatabase
import com.atomicasoftware.contactzillasync.db.Principal
import javax.inject.Inject

class PrincipalRepository @Inject constructor(
    db: AppDatabase
) {

    private val dao = db.principalDao()

    fun getBlocking(id: Long): Principal = dao.get(id)

}