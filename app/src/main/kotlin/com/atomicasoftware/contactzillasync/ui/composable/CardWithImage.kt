/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.ui.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atomicasoftware.contactzillasync.R
import com.atomicasoftware.contactzillasync.ui.UiUtils.isPortrait

@Composable
fun CardWithImage(
    title: String,
    modifier: Modifier = Modifier,
    image: Painter? = null,
    imageContentDescription: String? = null,
    imageAlignment: Alignment = Alignment.Center,
    imageContentScale: ContentScale = ContentScale.Crop,
    message: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Card(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            image?.let {
                if (isPortrait()) {
                    Image(
                        painter = it,
                        contentDescription = imageContentDescription,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 252.dp),
                        contentScale = imageContentScale,
                        alignment = imageAlignment
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = iconContentDescription,
                            modifier = Modifier
                                .size(44.dp)
                                .padding(end = 12.dp)
                        )
                    }

                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = title,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleLarge
                        )
                        subtitle?.let {
                            Text(
                                text = it,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                message?.let {
                    Text(
                        text = it,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                content()
            }
        }
    }
}

@Preview
@Composable
fun CardWithImage_Preview() {
    CardWithImage(
        image = painterResource(R.drawable.intro_permissions),
        title = "Demo card",
        message = "This is the message to be displayed under the title, but before the content."
    )
}

@Preview
@Composable
fun CardWithImage_Preview_WithIconAndSubtitleAndContent() {
    CardWithImage(
        title = "Demo card",
        icon = Icons.Default.TabletAndroid,
        subtitle = "Subtitle",
        message = "This is the message to be displayed under the title, but before the content."
    ) {
        Text("Content")
    }
}

@Preview
@Composable
fun CardWithImage_Preview_WithIconAndContentNoMessage() {
    CardWithImage(
        title = "Demo card",
        icon = Icons.Default.TabletAndroid
    ) {
        Text("Content")
    }
}
