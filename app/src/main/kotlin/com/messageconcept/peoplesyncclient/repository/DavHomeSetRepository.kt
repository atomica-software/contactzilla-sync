/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.repository

import android.accounts.Account
import androidx.room.Transaction
import com.messageconcept.peoplesyncclient.db.AppDatabase
import com.messageconcept.peoplesyncclient.db.HomeSet
import com.messageconcept.peoplesyncclient.db.Service
import javax.inject.Inject

class DavHomeSetRepository @Inject constructor(
    db: AppDatabase
) {

    private val dao = db.homeSetDao()

    fun getAddressBookHomeSetsFlow(account: Account) =
        dao.getBindableByAccountAndServiceTypeFlow(account.name, Service.TYPE_CARDDAV)

    fun getBindableByServiceFlow(serviceId: Long) = dao.getBindableByServiceFlow(serviceId)

    fun getByIdBlocking(id: Long) = dao.getById(id)

    fun getByServiceBlocking(serviceId: Long) = dao.getByService(serviceId)

    fun getCalendarHomeSetsFlow(account: Account) =
        dao.getBindableByAccountAndServiceTypeFlow(account.name, Service.TYPE_CALDAV)


    /**
     * Tries to insert new row, but updates existing row if already present.
     * This method preserves the primary key, as opposed to using "@Insert(onConflict = OnConflictStrategy.REPLACE)"
     * which will create a new row with incremented ID and thus breaks entity relationships!
     *
     * @return ID of the row, that has been inserted or updated. -1 If the insert fails due to other reasons.
     */
    @Transaction
    fun insertOrUpdateByUrlBlocking(homeset: HomeSet): Long =
        dao.getByUrl(homeset.serviceId, homeset.url.toString())?.let { existingHomeset ->
            dao.update(homeset.copy(id = existingHomeset.id))
            existingHomeset.id
        } ?: dao.insert(homeset)


    fun deleteBlocking(homeSet: HomeSet) = dao.delete(homeSet)

}