package fr.zyviotv.player.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.tv.tvFocusEffect

private data class LiveChannelPreview(
    val name: String,
    val category: String,
    val currentProgram: String,
    val nextProgram: String,
    val progress: Float,
)

private val previewChannels = listOf(
    LiveChannelPreview("Chaîne 1", "France", "Programme en direct", "Programme suivant", 0.42f),
    LiveChannelPreview("Chaîne 2", "Sports", "Match en direct", "Magazine sportif", 0.68f),
    LiveChannelPreview("Chaîne 3", "Information", "Journal", "Débat", 0.31f),
    LiveChannelPreview("Chaîne 4", "Divertissement", "Émission", "Série", 0.55f),
)

@Composable
fun LiveTvScreen(
    profile: DeviceProfile,
) {
    var selectedCategory by remember { mutableStateOf("Toutes") }
    var selectedChannel by remember { mutableStateOf(previewChannels.first()) }

    val categories = listOf("Toutes", "France", "Sports", "Information", "Divertissement")
    val filteredChannels = if (selectedCategory == "Toutes") {
        previewChannels
    } else {
        previewChannels.filter { it.category == selectedCategory }
    }

    if (profile == DeviceProfile.Mobile) {
        MobileLiveLayout(
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it },
            channels = filteredChannels,
            selectedChannel = selectedChannel,
            onChannelSelected = { selectedChannel = it },
        )
    } else {
        LargeScreenLiveLayout(
            isTelevision = profile == DeviceProfile.Television,
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it },
            channels = filteredChannels,
            selectedChannel = selectedChannel,
            onChannelSelected = { selectedChannel = it },
        )
    }
}

@Composable
private fun MobileLiveLayout(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    channels: List<LiveChannelPreview>,
    selectedChannel: LiveChannelPreview,
    onChannelSelected: (LiveChannelPreview) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        LiveHeader()
        Spacer(Modifier.height(16.dp))
        CategoryRow(categories, selectedCategory, onCategorySelected, isTelevision = false)
        Spacer(Modifier.height(16.dp))
        PlayerPreview(selectedChannel)
        Spacer(Modifier.height(18.dp))
        ChannelList(channels, selectedChannel, onChannelSelected, isTelevision = false)
    }
}

@Composable
private fun LargeScreenLiveLayout(
    isTelevision: Boolean,
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    channels: List<LiveChannelPreview>,
    selectedChannel: LiveChannelPreview,
    onChannelSelected: (LiveChannelPreview) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LiveHeader()
        Spacer(Modifier.height(14.dp))
        CategoryRow(categories, selectedCategory, onCategorySelected, isTelevision = isTelevision)
        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                ChannelList(channels, selectedChannel, onChannelSelected, isTelevision = isTelevision)
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
            ) {
                PlayerPreview(selectedChannel)
            }
        }
    }
}

@Composable
private fun LiveHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.RadioButtonChecked,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "TV en direct",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "Vos chaînes et programmes en cours",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    isTelevision: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        categories.forEach { category ->
            val selected = category == selectedCategory
            Card(
                modifier = Modifier
                    .tvFocusEffect(isTelevision, cornerRadiusDp = 999)
                    .clickable { onCategorySelected(category) },
                shape = RoundedCornerShape(999.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Text(
                    text = category,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun PlayerPreview(channel: LiveChannelPreview) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFF1E1E1E),
                            Color(0xFF080808),
                        ),
                    ),
                    shape = RoundedCornerShape(22.dp),
                )
                .height(260.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Lecteur vidéo natif prêt",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Maintenant",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = channel.currentProgram,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { channel.progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "À suivre : ${channel.nextProgram}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChannelList(
    channels: List<LiveChannelPreview>,
    selectedChannel: LiveChannelPreview,
    onChannelSelected: (LiveChannelPreview) -> Unit,
    isTelevision: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        channels.forEach { channel ->
            val selected = channel == selectedChannel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusEffect(isTelevision)
                    .clickable { onChannelSelected(channel) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(54.dp)
                            .height(54.dp)
                            .background(
                                Color(0xFF1D1D1D),
                                RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = channel.name,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = channel.currentProgram,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { channel.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
        }

        if (channels.isEmpty()) {
            Text(
                text = "Aucune chaîne dans cette catégorie.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
