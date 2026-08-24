package com.fyiplayer.app.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.data.prefs.Prefs
import kotlinx.coroutines.launch

// ISO 639-1, English first/default; curated to what NewPipeExtractor's YouTube client accepts.
internal val LANGUAGES = listOf(
    "en" to "English", "es" to "Spanish", "hi" to "Hindi", "bn" to "Bengali", "ar" to "Arabic",
    "pt" to "Portuguese", "ru" to "Russian", "ja" to "Japanese", "de" to "German", "fr" to "French",
    "it" to "Italian", "tr" to "Turkish", "ko" to "Korean", "vi" to "Vietnamese", "id" to "Indonesian",
    "ur" to "Urdu", "zh" to "Chinese", "nl" to "Dutch", "pl" to "Polish", "th" to "Thai",
)

// ISO 3166-1 alpha-2, US first/default.
internal val COUNTRIES = listOf(
    "US" to "United States", "BD" to "Bangladesh", "IN" to "India", "GB" to "United Kingdom",
    "DE" to "Germany", "FR" to "France", "JP" to "Japan", "KR" to "South Korea", "BR" to "Brazil",
    "RU" to "Russia", "TR" to "Turkey", "ID" to "Indonesia", "PK" to "Pakistan", "NG" to "Nigeria",
    "MX" to "Mexico", "CA" to "Canada", "AU" to "Australia", "ES" to "Spain", "IT" to "Italy",
    "NL" to "Netherlands", "PH" to "Philippines", "VN" to "Vietnam", "EG" to "Egypt", "SA" to "Saudi Arabia",
    "TH" to "Thailand",
)

/** Localization passed to NewPipeExtractor (see NewPipeInit): language shapes what titles,
 *  descriptions and dates come back in; country shapes what search and trending results surface,
 *  neither is a VPN substitute for a geo-blocked video. Same [LocaleDropdown] as the onboarding
 *  sheet, so a returning user sees one consistent picker, not chips here and dropdowns there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettings(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val language by prefs.contentLanguage.collectAsStateWithLifecycle(initialValue = "en")
    val country by prefs.contentCountry.collectAsStateWithLifecycle(initialValue = "US")

    SettingsSection("Language & region") {
        Column(Modifier.padding(horizontal = 16.dp)) {
            LocaleDropdown(
                label = "Language",
                options = LANGUAGES,
                selected = language,
                onSelect = { scope.launch { prefs.setContentLanguage(it) } },
            )
            LocaleDropdown(
                label = "Country",
                options = COUNTRIES,
                selected = country,
                onSelect = { scope.launch { prefs.setContentCountry(it) } },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
