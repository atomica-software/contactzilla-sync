/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomica.contactzillasync.repository

import com.atomica.contactzillasync.db.AppDatabase
import com.atomica.contactzillasync.db.Principal
import javax.inject.Inject

class PrincipalRepository @Inject constructor(
    db: AppDatabase
) {

    private val dao = db.principalDao()

    fun getBlocking(id: Long): Principal = dao.get(id)

}