package fr.zyviotv.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.zyviotv.player.ui.ZyvioTVPlayerApp
import fr.zyviotv.player.ui.theme.ZyvioTVTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZyvioTVTheme {
                ZyvioTVPlayerApp()
            }
        }
    }
}
