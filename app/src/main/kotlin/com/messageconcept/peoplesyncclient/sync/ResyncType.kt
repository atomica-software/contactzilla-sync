/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.sync

/**
 * Used to signal that re-synchronization is requested during a sync.
 *
 * Re-synchronization means that synchronization shouldn't skip listing/downloading
 * the entries even when then `sync-token` (or CTag) didn't change since the last sync.
 */
enum class ResyncType {

    /**
     * **(Normal) re-synchronization**: all remote entries shall be listed regardless of the
     * sync-token (or CTag) of the collection. Modified entries will then be downloaded as usual.
     *
     * Sample use-case: the past event time range setting has been modified, and we want
     * to get the new list of all events (regardless of the sync-token).
     */
    RESYNC_LIST,

    /**
     * **Full re-synchronization**: all remote entries shall be listed regardless of the
     * sync-token (or CTag) of the collection, and all entries will be downloaded again,
     * either if they were not changed on the server since the last sync.
     *
     * Sample use-case: Contact group type setting is changed, and all vCards have to
     * be downloaded and parsed again to determine their group memberships.
     */
    RESYNC_ENTRIES

}