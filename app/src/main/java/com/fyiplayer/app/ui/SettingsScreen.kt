package com.fyiplayer.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.BuildConfig
import com.fyiplayer.app.R
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.core.SponsorCategory
import com.fyiplayer.app.core.SponsorMode
import com.fyiplayer.app.core.Topic
import com.fyiplayer.app.data.prefs.Prefs
import com.fyiplayer.app.settings.prettyTreeUri
import com.fyiplayer.app.source.newpipe.YoutubeAuth
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing

private const val SUMMARY_SEPARATOR = " · "

/** One hub row: where it goes, how it looks, and the current value in words. */
private class HubRow(
    val page: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val summary: String,
)

/** The settings hub: eight rows, each opening its own page (SettingsPageScreen). The summary
 *  under each title is the current value, so most questions are answered without opening a page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenPage: (String) -> Unit) {
    val prefs = rememberFyiApp().prefs
    val rows = hubRows(prefs)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(title = { Text(stringResource(R.string.shell_tab_settings), style = MaterialTheme.typography.titleLarge) })
        LazyColumn(Modifier.fillMaxSize().padding(bottom = Spacing.s24)) {
            items(rows, key = { it.page }) { row ->
                HubRowItem(row, onClick = { onOpenPage(row.page) })
            }
        }
    }
}

@Composable
private fun HubRowItem(row: HubRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = Sizes.listTwoLine)
            .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(row.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.icon))
        Column(Modifier.weight(1f).padding(horizontal = Spacing.s16)) {
            Text(stringResource(row.titleRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                row.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Sizes.icon),
        )
    }
}

@Composable
private fun hubRows(prefs: Prefs): List<HubRow> = listOf(
    HubRow(SettingsPage.ACCOUNT, R.string.settings_account, Icons.Filled.Person, accountSummary()),
    HubRow(SettingsPage.CONTENT, R.string.settings_content, Icons.AutoMirrored.Filled.List, contentSummary(prefs)),
    HubRow(SettingsPage.PLAYBACK, R.string.settings_playback, Icons.Filled.PlayArrow, playbackSummary(prefs)),
    HubRow(SettingsPage.SKIPPING, R.string.settings_skipping, Icons.Filled.ArrowForward, skippingSummary(prefs)),
    HubRow(SettingsPage.DOWNLOADS, R.string.settings_downloads, Icons.Filled.KeyboardArrowDown, downloadsSummary(prefs)),
    HubRow(SettingsPage.HISTORY, R.string.settings_history, Icons.Filled.Refresh, historySummary(prefs)),
    HubRow(SettingsPage.BACKUP, R.string.settings_backup, Icons.Filled.Share, stringResource(R.string.settings_summary_backup)),
    HubRow(SettingsPage.ABOUT, R.string.settings_about, Icons.Filled.Info, stringResource(R.string.settings_summary_version, BuildConfig.VERSION_NAME)),
)

@Composable
private fun accountSummary(): String {
    val signedIn by YoutubeAuth.isLoggedIn.collectAsStateWithLifecycle()
    return stringResource(if (signedIn) R.string.settings_summary_signed_in else R.string.settings_summary_signed_out)
}

@Composable
private fun contentSummary(prefs: Prefs): String {
    val enabledSources by prefs.enabledSources.collectAsStateWithLifecycle(initialValue = emptySet())
    val topics by prefs.exploreTopics.collectAsStateWithLifecycle(initialValue = emptySet())
    val language by prefs.contentLanguage.collectAsStateWithLifecycle(initialValue = "en")
    val country by prefs.contentCountry.collectAsStateWithLifecycle(initialValue = "US")
    val sourceNames = SourceRegistry.all.filter { it.id in enabledSources }.joinToString(", ") { it.displayName }
    val topicNames = Topic.entries.filter { it.name in topics }.joinToString(", ") { it.label }
    val locale = stringResource(R.string.settings_summary_locale, language, country)
    return listOf(sourceNames, topicNames, locale).filter { it.isNotEmpty() }.joinToString(SUMMARY_SEPARATOR)
}

@Composable
private fun playbackSummary(prefs: Prefs): String {
    val wifiHeight by prefs.maxResolutionWifi.collectAsStateWithLifecycle(initialValue = 1080)
    val mobileHeight by prefs.maxResolutionMobile.collectAsStateWithLifecycle(initialValue = 720)
    val background by prefs.backgroundPlayback.collectAsStateWithLifecycle(initialValue = true)
    val heights = stringResource(R.string.settings_summary_playback, wifiHeight, mobileHeight)
    if (!background) return heights
    return heights + SUMMARY_SEPARATOR + stringResource(R.string.settings_summary_background_on)
}

@Composable
private fun skippingSummary(prefs: Prefs): String {
    val enabled by prefs.sponsorBlock.collectAsStateWithLifecycle(initialValue = false)
    val modes by prefs.sponsorModes.collectAsStateWithLifecycle(
        initialValue = SponsorCategory.entries.associateWith { it.defaultMode },
    )
    if (!enabled) return stringResource(R.string.settings_value_off)
    val skipped = modes.values.count { it == SponsorMode.AUTO_SKIP }
    val withButton = modes.values.count { it == SponsorMode.SHOW_BUTTON }
    return stringResource(R.string.settings_summary_skipping, skipped, withButton)
}

@Composable
private fun downloadsSummary(prefs: Prefs): String {
    val treeUri by prefs.downloadTreeUri.collectAsStateWithLifecycle(initialValue = null)
    return prettyTreeUri(treeUri, stringResource(R.string.settings_download_folder_default))
}

@Composable
private fun historySummary(prefs: Prefs): String {
    val watch by prefs.recordWatchHistory.collectAsStateWithLifecycle(initialValue = true)
    val search by prefs.recordSearchHistory.collectAsStateWithLifecycle(initialValue = true)
    val summaryRes = when {
        watch && search -> R.string.settings_summary_history_both
        watch -> R.string.settings_summary_history_watch
        search -> R.string.settings_summary_history_search
        else -> R.string.settings_value_off
    }
    return stringResource(summaryRes)
}
