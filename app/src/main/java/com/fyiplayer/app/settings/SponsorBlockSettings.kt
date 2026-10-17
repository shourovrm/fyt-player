package com.fyiplayer.app.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.R
import com.fyiplayer.app.core.SponsorCategory
import com.fyiplayer.app.core.SponsorMode
import com.fyiplayer.app.data.prefs.Prefs
import kotlinx.coroutines.launch

/** The SponsorBlock master switch and one mode per category. Channel exceptions are set from the
 *  video page of the channel itself, not here. */
@Composable
fun SponsorBlockSettings(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val enabled by prefs.sponsorBlock.collectAsStateWithLifecycle(initialValue = false)
    val modes by prefs.sponsorModes.collectAsStateWithLifecycle(
        initialValue = SponsorCategory.entries.associateWith { it.defaultMode },
    )

    SettingsSection(null) {
        SettingsSwitchRow(
            label = stringResource(R.string.settings_sponsorblock_skip),
            hint = stringResource(R.string.settings_sponsorblock_skip_hint),
            checked = enabled,
            onCheckedChange = { scope.launch { prefs.setSponsorBlock(it) } },
        )
        SponsorCategory.entries.forEach { category ->
            CategoryRow(
                category = category,
                mode = modes[category] ?: category.defaultMode,
                enabled = enabled,
                onSelect = { mode -> scope.launch { prefs.setSponsorMode(category, mode) } },
            )
        }
    }
}

/** One category: its short name and current mode; tapping opens a menu of the three modes. */
@Composable
private fun CategoryRow(category: SponsorCategory, mode: SponsorMode, enabled: Boolean, onSelect: (SponsorMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingsValueRow(
            label = stringResource(category.shortLabelRes()),
            value = stringResource(mode.shortLabelRes()),
            opens = ValueRowOpens.MENU,
            onClick = { expanded = true },
            enabled = enabled,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SponsorMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.shortLabelRes())) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

// SponsorCategory.label and SponsorMode.label stay as they are: the player and skip button use them.
@StringRes
private fun SponsorCategory.shortLabelRes(): Int = when (this) {
    SponsorCategory.SPONSOR -> R.string.settings_category_sponsor
    SponsorCategory.INTRO -> R.string.settings_category_intro
    SponsorCategory.OUTRO -> R.string.settings_category_credits
    SponsorCategory.INTERACTION -> R.string.settings_category_reminder
    SponsorCategory.SELF_PROMO -> R.string.settings_category_promotion
    SponsorCategory.MUSIC_OFFTOPIC -> R.string.settings_category_non_music
    SponsorCategory.PREVIEW -> R.string.settings_category_recap
    SponsorCategory.FILLER -> R.string.settings_category_filler
}

@StringRes
private fun SponsorMode.shortLabelRes(): Int = when (this) {
    SponsorMode.OFF -> R.string.settings_value_off
    SponsorMode.AUTO_SKIP -> R.string.settings_mode_skip
    SponsorMode.SHOW_BUTTON -> R.string.settings_mode_button
}
