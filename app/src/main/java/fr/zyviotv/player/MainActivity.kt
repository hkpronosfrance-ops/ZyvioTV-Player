package fr.zyviotv.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.util.UnstableApi
import fr.zyviotv.player.data.settings.InterfaceLanguageController
import fr.zyviotv.player.data.settings.OnboardingSetupPreferences
import fr.zyviotv.player.ui.ZyvioTVPlayerApp
import fr.zyviotv.player.ui.diagnostics.FrameStatsMonitor
import fr.zyviotv.player.ui.theme.ZyvioTVTheme

@UnstableApi
class MainActivity : ComponentActivity() {
    private val pendingDeepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        InterfaceLanguageController.apply(
            this,
            OnboardingSetupPreferences(this).device().interfaceLanguage,
        )
        pendingDeepLink.value = intent?.dataString
        enableEdgeToEdge()
        FrameStatsMonitor.attach(this)
        setContent {
            ZyvioTVTheme {
                ZyvioTVPlayerApp(
                    deepLink = pendingDeepLink.value,
                    onDeepLinkConsumed = { pendingDeepLink.value = null },
                )
            }
        }
    }

    override fun onStop() {
        // One frame summary for the screen in use when the app is left.
        FrameStatsMonitor.flush()
        super.onStop()
    }

    override fun onDestroy() {
        FrameStatsMonitor.detach(this)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink.value = intent.dataString
    }
}
