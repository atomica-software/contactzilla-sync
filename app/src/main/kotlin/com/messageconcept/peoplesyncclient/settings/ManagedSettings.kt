/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.messageconcept.peoplesyncclient.settings

import javax.inject.Inject

class ManagedSettings @Inject constructor(
    private val settingsManager: SettingsManager,
)  {

    companion object {
        private const val KEY_LOGIN_BASE_URL = "login_base_url"
        private const val KEY_LOGIN_USER_NAME = "login_user_name"
    }

    fun getBaseUrl(): String? {
        return settingsManager.getString(KEY_LOGIN_BASE_URL)
    }

    fun getUsername(): String? {
        return settingsManager.getString(KEY_LOGIN_USER_NAME)
    }
}