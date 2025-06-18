/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomica.contactzillasync.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

@Suppress("MemberVisibilityCanBePrivate")
object M3ColorScheme {

    val primaryLight = Color(0xff64af2a)
    val onPrimaryLight = Color(0xFFffffff)
    val secondaryLight = Color(0xff7DCF3E)
    val onSecondaryLight = Color(0xFFffffff)
    val tertiaryLight = Color(0xff4A8020)
    val onTertiaryLight = Color(0xFFffffff)

    val primaryDark = Color(0xff8BC85A)
    val onPrimaryDark = Color(0xFF000000)
    val secondaryDark = Color(0xff8BC85A)
    val onSecondaryDark = Color(0xFF000000)
    val tertiaryDark = Color(0xff64af2a)
    val onTertiaryDark = Color(0xFFffffff)


    val lightScheme = lightColorScheme(
        primary = primaryLight,
        onPrimary = onPrimaryLight,
        secondary = secondaryLight,
        onSecondary = onSecondaryLight,
        tertiary = tertiaryLight,
        onTertiary = onTertiaryLight,
    )

    val darkScheme = darkColorScheme(
        primary = primaryLight,
        onPrimary = onPrimaryLight,
        secondary = secondaryDark,
        onSecondary = onSecondaryDark,
        tertiary = tertiaryDark,
        onTertiary = onTertiaryDark,
    )

}