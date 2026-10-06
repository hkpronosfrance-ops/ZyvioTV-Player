package fr.zyviotv.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.util.UnstableApi
import fr.zyviotv.player.ui.ZyvioTVPlayerApp
import fr.zyviotv.player.ui.theme.ZyvioTVTheme

@UnstableApi
class MainActivity : ComponentActivity() {
    private val pendingDeepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDeepLink.value = intent?.dataString
        enableEdgeToEdge()
        setContent {
            ZyvioTVTheme {
                ZyvioTVPlayerApp(
                    deepLink = pendingDeepLink.value,
                    onDeepLinkConsumed = { pendingDeepLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink.value = intent.dataString
    }
}
