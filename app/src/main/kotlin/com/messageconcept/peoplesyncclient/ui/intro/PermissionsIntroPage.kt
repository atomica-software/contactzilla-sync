/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomica.contactzillasync.ui.intro

import android.content.Context
import androidx.compose.runtime.Composable
import com.atomica.contactzillasync.ui.PermissionsModel
import com.atomica.contactzillasync.ui.PermissionsScreen
import com.atomica.contactzillasync.util.PermissionUtils
import com.atomica.contactzillasync.util.PermissionUtils.CONTACT_PERMISSIONS
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PermissionsIntroPage @Inject constructor(
    @ApplicationContext private val context: Context
): IntroPage() {

    var model: PermissionsModel? = null

    override fun getShowPolicy(): ShowPolicy {
        // show PermissionsFragment as intro fragment when no permissions are granted
        val permissions = CONTACT_PERMISSIONS
        return if (PermissionUtils.haveAnyPermission(context, permissions))
            ShowPolicy.DONT_SHOW
        else
            ShowPolicy.SHOW_ALWAYS
    }

    @Composable
    override fun ComposePage() {
        PermissionsScreen()
    }

}