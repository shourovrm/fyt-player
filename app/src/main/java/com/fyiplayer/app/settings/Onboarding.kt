package com.fyiplayer.app.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.data.prefs.Prefs
import kotlinx.coroutines.launch

/** First-run language/country prompt over Home (DESIGN.md-style: a sheet, not a blocking page --
 *  Home stays visible so it reads as a setting, not a wall). Only the Done button dismisses it
 *  for good; see the onDismissRequest comment below for why. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingSheet(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val language by prefs.contentLanguage.collectAsStateWithLifecycle(initialValue = "en")
    val country by prefs.contentCountry.collectAsStateWithLifecycle(initialValue = "US")
    val sheetState = rememberModalBottomSheetState()

    // No-op, not setOnboardingDone(): the system notification-permission prompt (MainActivity)
    // can appear over this sheet on first launch and its focus change alone fires onDismissRequest
    // -- persisting "done" there would silently skip onboarding. Only the Done button persists it;
    // any other dismissal (swipe, back, outside tap) just re-shows the sheet next visit.
    ModalBottomSheet(onDismissRequest = {}, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("Language & country", style = MaterialTheme.typography.titleLarge)
            Text(
                "Sets search results, trending charts and captions. Change anytime in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
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
            Button(
                onClick = { scope.launch { prefs.setOnboardingDone() } },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
            ) { Text("Done") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocaleDropdown(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (code, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(code); expanded = false })
            }
        }
    }
}
