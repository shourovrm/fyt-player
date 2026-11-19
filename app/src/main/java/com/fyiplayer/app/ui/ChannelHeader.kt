package com.fyiplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.fyiplayer.app.R
import com.fyiplayer.app.core.ChannelProfile
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.ui.theme.ControlShape
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing

private const val DESCRIPTION_MAX_LINES = 2

/**
 * Profile block above the channel tabs. [profile] is null until the first page arrives; the
 * [listing] fills in meanwhile so the picture and name do not pop in. Play all is offered only
 * when [canPlayAll], and the Subscribe button then takes the whole row.
 */
@Composable
internal fun ChannelHeader(
    listing: Listing,
    profile: ChannelProfile?,
    subscribed: Boolean,
    canPlayAll: Boolean,
    onToggleSubscribe: () -> Unit,
    onPlayAll: () -> Unit,
) {
    val name = (profile?.name ?: listing.title).ifBlank { stringResource(R.string.channel_title_fallback) }
    val subscriberText = profile?.subscriberCount?.let { stringResource(R.string.results_subscribers, compactCount(it)) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) {
        ProfilePicture(profile?.avatarUrl ?: listing.thumbnailUrl, name)
        Text(name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (subscriberText != null) {
            Text(subscriberText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (profile?.description != null) {
            Text(
                profile.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = DESCRIPTION_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            Button(onClick = onToggleSubscribe, modifier = Modifier.weight(1f), shape = ControlShape) {
                Text(stringResource(if (subscribed) R.string.action_unsubscribe else R.string.action_subscribe))
            }
            if (canPlayAll) {
                OutlinedButton(onClick = onPlayAll, modifier = Modifier.weight(1f), shape = ControlShape) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.channel_play_all))
                }
            }
        }
    }
}

/** Round picture; until an image URL is known, the channel's first letter on a flat circle. */
@Composable
private fun ProfilePicture(imageUrl: String?, name: String) {
    Box(
        Modifier.size(Sizes.channelAvatarLarge).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
        }
    }
}
