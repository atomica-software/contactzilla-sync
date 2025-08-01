/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SettingsHeader(divider: Boolean = false, content: @Composable () -> Unit) {
    if (divider)
        Divider(Modifier.padding(vertical = 8.dp))

    Row(
        Modifier
            .padding(top = 16.dp, start = 52.dp, end = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.secondary
            )
        ) {
            content()
        }
    }
}

@Composable
@Preview
fun SettingsHeader_Sample() {
    Column {
        SettingsHeader(divider = true) {
            Text("Some Settings Section")
        }
    }
}


@Composable
fun Setting(
    icon: @Composable () -> Unit,
    name: @Composable () -> Unit,
    summary: String?,
    end: @Composable () -> Unit = {},
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    var modifier = Modifier.fillMaxWidth()
    modifier = if (enabled)
        modifier.clickable(onClick = onClick)
    else
        modifier.alpha(.38f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Column(
            Modifier
                .padding(start = 8.dp)
                .weight(1f)
        ) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                name()
            }

            if (summary != null)
                Text(summary, style = MaterialTheme.typography.bodyMedium)
        }

        end()
    }
}

@Composable
fun Setting(
    name: String,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    Setting(
        icon = {
            if (icon != null)
                Icon(icon, contentDescription = name)
        },
        name = {
            Text(name, style = MaterialTheme.typography.bodyLarge)
        },
        summary = summary,
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
@Preview
fun Setting_Sample() {
    Setting(
        icon = Icons.Default.Folder,
        name = "Setting",
        summary = "Currently off"
    )
}

@Composable
@Preview
fun Setting_Sample_Disabled() {
    Setting(
        icon = Icons.Default.Folder,
        enabled = false,
        name = "Setting",
        summary = "Currently off"
    )
}

@Composable
fun SwitchSetting(
    checked: Boolean,
    name: String,
    summaryOn: String? = null,
    summaryOff: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    Setting(
        icon = {
            if (icon != null)
                Icon(icon, name)
        },
        name = {
            Text(name)
        },
        summary = if (checked) summaryOn else summaryOff,
        end = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        },
        enabled = enabled
    ) {
        onCheckedChange(!checked)
    }
}

@Composable
@Preview
fun SwitchSetting_Sample() {
    SwitchSetting(
        name = "Some Switched Setting",
        checked = true,
        summaryOn = "Currently on"
    )
}