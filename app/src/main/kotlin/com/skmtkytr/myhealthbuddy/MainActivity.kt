package com.skmtkytr.myhealthbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.skmtkytr.myhealthbuddy.ui.ChatScreen
import com.skmtkytr.myhealthbuddy.ui.ChatViewModel
import com.skmtkytr.myhealthbuddy.ui.HomeScreen
import com.skmtkytr.myhealthbuddy.ui.HomeViewModel
import com.skmtkytr.myhealthbuddy.ui.SettingsScreen
import com.skmtkytr.myhealthbuddy.ui.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val homeVm: HomeViewModel by viewModels { HomeViewModel.Factory }
    private val chatVm: ChatViewModel by viewModels { ChatViewModel.Factory }
    private val settingsVm: SettingsViewModel by viewModels { SettingsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MhbApp(homeVm, chatVm, settingsVm)
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("ヘルス", Icons.Filled.Favorite),
    Chat("チャット", Icons.AutoMirrored.Filled.Send),
    Settings("設定", Icons.Filled.Settings),
}

@Composable
private fun MhbApp(
    homeVm: HomeViewModel,
    chatVm: ChatViewModel,
    settingsVm: SettingsViewModel,
) {
    var tab by remember { mutableStateOf(Tab.Home) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { inner: PaddingValues ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(inner)) {
            when (tab) {
                Tab.Home -> HomeScreen(homeVm)
                Tab.Chat -> ChatScreen(chatVm)
                Tab.Settings -> SettingsScreen(settingsVm)
            }
        }
    }
}
