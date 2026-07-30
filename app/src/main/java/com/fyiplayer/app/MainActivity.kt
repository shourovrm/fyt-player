package com.fyiplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fyiplayer.app.player.MiniPlayer
import com.fyiplayer.app.player.QueueBar
import com.fyiplayer.app.ui.AppScaffold
import com.fyiplayer.app.ui.AppShell
import com.fyiplayer.app.ui.openDetail
import com.fyiplayer.app.ui.theme.FyiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FyiTheme {
                AppScaffold(
                    queueBar = { QueueBar() },
                    miniPlayer = { nav -> MiniPlayer(onOpen = { nav.openDetail(it) }) },
                ) { AppShell(it) }
            }
        }
    }
}
