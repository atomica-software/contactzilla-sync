/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomica.contactzillasync.ui.intro

import javax.inject.Inject

class OseIntroPageFactory @Inject constructor(
): IntroPageFactory {

    override val introPages = arrayOf<IntroPage>(
        // All intro pages removed - functionality handled by StartupPermissionManager
    )

}