package com.fyiplayer.app.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.R
import com.fyiplayer.app.data.prefs.Prefs
import com.fyiplayer.app.ui.HomeLayout
import com.fyiplayer.app.ui.theme.ControlShape
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/** The layout Home opens in. The app-bar icon on Home only changes it until the app closes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeLayoutSettings(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val selected by prefs.homeLayout.collectAsStateWithLifecycle(initialValue = HomeLayout.FULL_CARD)

    SettingsSection(stringResource(R.string.settings_home_layout)) {
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
        ) {
            HomeLayout.entries.forEachIndexed { index, layout ->
                SegmentedButton(
                    selected = layout == selected,
                    onClick = { scope.launch { prefs.setHomeLayout(layout) } },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = HomeLayout.entries.size,
                        baseShape = ControlShape,
                    ),
                    modifier = Modifier.height(Sizes.touchTarget),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    icon = {},
                    label = { Text(stringResource(homeLayoutLabel(layout))) },
                )
            }
        }
    }
}

private fun homeLayoutLabel(layout: HomeLayout): Int = when (layout) {
    HomeLayout.FULL_CARD -> R.string.settings_home_layout_card
    HomeLayout.COMPACT -> R.string.settings_home_layout_compact
    HomeLayout.GRID -> R.string.settings_home_layout_grid
}
