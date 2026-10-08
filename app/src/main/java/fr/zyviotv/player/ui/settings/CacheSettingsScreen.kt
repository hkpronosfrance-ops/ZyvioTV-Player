package fr.zyviotv.player.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import fr.zyviotv.player.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CacheSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cacheBytes by remember { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableLongStateOf(0L) }

    LaunchedEffect(refreshToken) {
        cacheBytes = withContext(Dispatchers.IO) {
            context.applicationContext.computeCacheSize()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                enabled = !busy,
                onClick = onBack,
            ) {
                Text(stringResource(R.string.nav_back))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_data_cache),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = stringResource(R.string.settings_device_storage),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.settings_temporary_cache),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = formatBytes(cacheBytes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_cache_explanation),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        message?.let {
            Spacer(Modifier.height(14.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(18.dp))

        Button(
            enabled = !busy && cacheBytes > 0L,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    busy = true
                    message = null
                    val success = withContext(Dispatchers.IO) {
                        context.applicationContext.clearCacheSafely()
                    }
                    refreshToken += 1L
                    message = if (success) {
                        "Cache vidé."
                    } else {
                        "Une partie du cache n’a pas pu être supprimée."
                    }
                    busy = false
                }
            },
        ) {
            Text(if (busy) "Nettoyage…" else "Vider le cache")
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.settings_about_storage),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_secure_storage),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun Context.computeCacheSize(): Long =
    cacheDir.walkTopDown()
        .filter { it.isFile }
        .sumOf { it.length() }

private fun Context.clearCacheSafely(): Boolean {
    var success = true
    cacheDir.listFiles().orEmpty().forEach { file ->
        if (!file.deleteRecursively()) success = false
    }
    return success
}

private fun formatBytes(value: Long): String {
    if (value < 1024L) return "$value o"
    val kb = value / 1024.0
    if (kb < 1024.0) return String.format("%.1f Ko", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format("%.1f Mo", mb)
    val gb = mb / 1024.0
    return String.format("%.2f Go", gb)
}
