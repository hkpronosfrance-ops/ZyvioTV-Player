package fr.zyviotv.player.ui.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.shared.epg.EpgProgramme
import fr.zyviotv.player.shared.epg.EpgWindow
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.theme.ZyvioRedTint
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioSurface2
import fr.zyviotv.player.ui.theme.ZyvioSurface3
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import fr.zyviotv.player.ui.theme.ZyvioTextTertiary
import fr.zyviotv.player.ui.tv.tvFocusEffect
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

data class EpgChannelUi(
    val id: String,
    val number: String?,
    val name: String,
    val sourceLabel: String? = null,
    val programmes: List<EpgProgramme> = emptyList(),
)

sealed interface EpgGuideState {
    data object Loading : EpgGuideState
    data class Ready(val channels: List<EpgChannelUi>) : EpgGuideState
    data class Error(val message: String) : EpgGuideState
}

@Composable
fun GuideEpgScreen(
    profile: DeviceProfile,
    state: EpgGuideState = EpgGuideState.Ready(emptyList()),
    nowEpochSeconds: Long = System.currentTimeMillis() / 1000L,
    onRetry: () -> Unit = {},
    onWatchChannel: (EpgChannelUi) -> Unit = {},
    onWatchProgramme: (EpgChannelUi, EpgProgramme) -> Unit = { _, _ -> },
    onRemindProgramme: (EpgChannelUi, EpgProgramme) -> Unit = { _, _ -> },
) {
    when (state) {
        EpgGuideState.Loading -> GuideLoading()
        is EpgGuideState.Error -> GuideError(state.message, onRetry)
        is EpgGuideState.Ready -> GuideReady(
            profile = profile,
            channels = state.channels,
            nowEpochSeconds = nowEpochSeconds,
            onWatchChannel = onWatchChannel,
            onWatchProgramme = onWatchProgramme,
            onRemindProgramme = onRemindProgramme,
        )
    }
}

@Composable
private fun GuideReady(
    profile: DeviceProfile,
    channels: List<EpgChannelUi>,
    nowEpochSeconds: Long,
    onWatchChannel: (EpgChannelUi) -> Unit,
    onWatchProgramme: (EpgChannelUi, EpgProgramme) -> Unit,
    onRemindProgramme: (EpgChannelUi, EpgProgramme) -> Unit,
) {
    val window = remember(nowEpochSeconds) { EpgWindow.around(nowEpochSeconds) }
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scale = when (profile) {
        DeviceProfile.Mobile -> 4.dp
        DeviceProfile.Tablet -> 5.dp
        DeviceProfile.Television -> 10.dp
    }
    val channelWidth = when (profile) {
        DeviceProfile.Mobile -> 96.dp
        DeviceProfile.Tablet -> 168.dp
        DeviceProfile.Television -> 360.dp
    }
    var selected by remember { mutableStateOf<Pair<EpgChannelUi, EpgProgramme>?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Guide TV",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "Heure locale de l'appareil",
                    color = ZyvioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val minutesFromStart = ((nowEpochSeconds - window.fromEpochSeconds) / 60L).toInt()
                        horizontal.animateScrollTo((minutesFromStart * scale.value).toInt())
                    }
                },
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Maintenant")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (channels.isEmpty()) {
            GuideEmpty()
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(vertical),
        ) {
            Row {
                Surface(
                    modifier = Modifier
                        .width(channelWidth)
                        .height(44.dp),
                    color = ZyvioSurface1,
                ) {
                    Box(contentAlignment = Alignment.CenterStart) {
                        Text(
                            text = "Chaînes",
                            modifier = Modifier.padding(horizontal = 12.dp),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .horizontalScroll(horizontal)
                        .height(44.dp),
                ) {
                    timeMarkers(window, scale).forEach { marker ->
                        Surface(
                            modifier = Modifier.width(marker.width),
                            color = ZyvioSurface1,
                        ) {
                            Box(contentAlignment = Alignment.CenterStart) {
                                Text(
                                    text = marker.label,
                                    modifier = Modifier.padding(start = 8.dp),
                                    color = ZyvioTextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            channels.take(MAX_VISIBLE_CHANNELS).forEach { channel ->
                Row(
                    modifier = Modifier.height(if (profile == DeviceProfile.Television) 88.dp else 72.dp),
                ) {
                    ChannelCell(
                        channel = channel,
                        width = channelWidth,
                        isTelevision = profile == DeviceProfile.Television,
                        onClick = {
                            if (channel.programmes.isEmpty()) onWatchChannel(channel)
                        },
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(horizontal),
                    ) {
                        val programmes = channel.programmes
                            .filter(window::contains)
                            .sortedBy { it.startEpochSeconds }

                        if (programmes.isEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .width(totalTimelineWidth(scale))
                                    .fillMaxSize()
                                    .tvFocusEffect(profile == DeviceProfile.Television)
                                    .clickable { onWatchChannel(channel) },
                                color = ZyvioSurface1,
                            ) {
                                Box(
                                    modifier = Modifier.padding(12.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(
                                        text = "Programme non disponible",
                                        color = ZyvioTextTertiary,
                                    )
                                }
                            }
                        } else {
                            programmes.forEach { programme ->
                                ProgrammeBlock(
                                    programme = programme,
                                    nowEpochSeconds = nowEpochSeconds,
                                    scale = scale,
                                    isTelevision = profile == DeviceProfile.Television,
                                    onClick = { selected = channel to programme },
                                )
                            }
                        }
                    }
                }
            }

            selected?.let { (channel, programme) ->
                Spacer(Modifier.height(16.dp))
                ProgrammeDetails(
                    channel = channel,
                    programme = programme,
                    nowEpochSeconds = nowEpochSeconds,
                    onWatch = { onWatchProgramme(channel, programme) },
                    onRemind = { onRemindProgramme(channel, programme) },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChannelCell(
    channel: EpgChannelUi,
    width: Dp,
    isTelevision: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(width)
            .fillMaxSize()
            .tvFocusEffect(isTelevision, cornerRadiusDp = 0)
            .clickable(onClick = onClick),
        color = ZyvioSurface1,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = listOfNotNull(channel.number, channel.name).joinToString("  "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            channel.sourceLabel?.let {
                Text(
                    text = it,
                    color = ZyvioTextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ProgrammeBlock(
    programme: EpgProgramme,
    nowEpochSeconds: Long,
    scale: Dp,
    isTelevision: Boolean,
    onClick: () -> Unit,
) {
    val durationMinutes = ((programme.endEpochSeconds - programme.startEpochSeconds) / 60L)
        .coerceAtLeast(1L)
    val width = (durationMinutes * scale.value).dp.coerceAtLeast(if (isTelevision) 16.dp else 8.dp)
    val isCurrent = nowEpochSeconds >= programme.startEpochSeconds && nowEpochSeconds < programme.endEpochSeconds
    val isPast = nowEpochSeconds >= programme.endEpochSeconds
    val color = when {
        isCurrent -> ZyvioSurface3
        isPast -> ZyvioSurface1
        else -> ZyvioSurface2
    }

    Surface(
        modifier = Modifier
            .width(width)
            .fillMaxSize()
            .padding(end = 2.dp)
            .tvFocusEffect(isTelevision, cornerRadiusDp = 8)
            .clickable(onClick = onClick),
        color = color,
        shape = RoundedCornerShape(8.dp),
    ) {
        Box {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                if (durationMinutes >= 10) {
                    Text(
                        text = programme.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isPast) ZyvioTextTertiary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    )
                } else {
                    Text("•", color = ZyvioTextSecondary)
                }
                if (isCurrent) {
                    Text(
                        text = "EN COURS",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(if (isTelevision) 4.dp else 2.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun ProgrammeDetails(
    channel: EpgChannelUi,
    programme: EpgProgramme,
    nowEpochSeconds: Long,
    onWatch: () -> Unit,
    onRemind: () -> Unit,
) {
    val future = programme.startEpochSeconds > nowEpochSeconds
    val current = nowEpochSeconds >= programme.startEpochSeconds && nowEpochSeconds < programme.endEpochSeconds

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZyvioSurface1,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = channel.name,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = programme.title,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = formatRange(programme.startEpochSeconds, programme.endEpochSeconds),
                modifier = Modifier.padding(top = 6.dp),
                color = ZyvioTextSecondary,
            )
            programme.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 10.dp),
                    color = ZyvioTextSecondary,
                )
            }
            Spacer(Modifier.height(16.dp))

            when {
                current -> Button(onClick = onWatch) {
                    Icon(Icons.Default.LiveTv, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Regarder")
                }

                future -> OutlinedButton(onClick = onRemind) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Me rappeler")
                }

                else -> Text(
                    text = "Programme terminé",
                    color = ZyvioTextTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun GuideLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Chargement du guide…")
        }
    }
}

@Composable
private fun GuideError(
    message: String,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = ZyvioSurface1,
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Guide indisponible",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = message,
                    modifier = Modifier.padding(top = 8.dp),
                    color = ZyvioTextSecondary,
                )
                Spacer(Modifier.height(18.dp))
                OutlinedButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Réessayer")
                }
            }
        }
    }
}

@Composable
private fun GuideEmpty() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = ZyvioSurface1,
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Guide indisponible",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Les chaînes restent accessibles depuis TV en direct.",
                    modifier = Modifier.padding(top = 6.dp),
                    color = ZyvioTextSecondary,
                )
            }
        }
    }
}

private data class TimeMarker(val label: String, val width: Dp)

private fun timeMarkers(window: EpgWindow, scale: Dp): List<TimeMarker> {
    val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    val markerSeconds = 30 * 60L
    val count = ((window.toEpochSeconds - window.fromEpochSeconds) / markerSeconds).toInt()
    return List(count) { index ->
        val epoch = window.fromEpochSeconds + index * markerSeconds
        TimeMarker(
            label = formatter.format(Instant.ofEpochSecond(epoch)),
            width = (30 * scale.value).dp,
        )
    }
}

private fun totalTimelineWidth(scale: Dp): Dp = (9 * 60 * scale.value).dp

private fun formatRange(start: Long, end: Long): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochSecond(start)) + " – " +
        formatter.format(Instant.ofEpochSecond(end))
}

private const val MAX_VISIBLE_CHANNELS = 50
