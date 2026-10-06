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
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.settings.ParentalUnlockDialog
import fr.zyviotv.player.ui.theme.ZyvioRedTint
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioSurface2
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import fr.zyviotv.player.ui.tv.tvFocusEffect
import kotlinx.coroutines.delay

data class LiveChannelUi(
    val id: String,
    val name: String,
    val category: String,
    val channelNumber: String? = null,
    val currentProgram: String? = null,
    val nextProgram: String? = null,
    val progress: Float? = null,
    val isLocked: Boolean = false,
)

sealed interface LiveScreenState {
    data object Loading : LiveScreenState
    data class Ready(
        val channels: List<LiveChannelUi>,
        val lockedCategories: Set<String> = emptySet(),
    ) : LiveScreenState
    data class Error(val message: String) : LiveScreenState
}

@Composable
fun LiveTvScreen(
    profile: DeviceProfile,
    state: LiveScreenState = LiveScreenState.Ready(emptyList()),
    onRetry: () -> Unit = {},
    onPreviewChannel: (LiveChannelUi) -> Unit = {},
    onTuneChannel: (LiveChannelUi) -> Unit = {},
    onOpenGuide: () -> Unit = {},
) {
    when (state) {
        LiveScreenState.Loading -> LiveLoadingState()
        is LiveScreenState.Error -> LiveErrorState(
            message = state.message,
            onRetry = onRetry,
        )
        is LiveScreenState.Ready -> LiveReadyState(
            profile = profile,
            channels = state.channels,
            lockedCategories = state.lockedCategories,
            onPreviewChannel = onPreviewChannel,
            onTuneChannel = { channel ->
                if (channel.isLocked) pendingChannel = channel else onTuneChannel(channel)
            },
            onOpenGuide = onOpenGuide,
        )
    }

    ParentalUnlockDialog(
        visible = pendingCategory != null,
        title = "Catégorie verrouillée",
        onDismiss = { pendingCategory = null },
        onUnlocked = {
            val category = pendingCategory ?: return@ParentalUnlockDialog
            pendingCategory = null
            selectedCategory = category
            selectedChannelId = channels.firstOrNull { channel ->
                category == "Toutes" || channel.category == category
            }?.id
        },
    )

    ParentalUnlockDialog(
        visible = pendingChannel != null,
        title = "Chaîne verrouillée",
        onDismiss = { pendingChannel = null },
        onUnlocked = {
            val channel = pendingChannel ?: return@ParentalUnlockDialog
            pendingChannel = null
            onTuneChannel(channel)
        },
    )
}

@Composable
private fun LiveReadyState(
    profile: DeviceProfile,
    channels: List<LiveChannelUi>,
    lockedCategories: Set<String>,
    onPreviewChannel: (LiveChannelUi) -> Unit,
    onTuneChannel: (LiveChannelUi) -> Unit,
    onOpenGuide: () -> Unit,
) {
    val categories = remember(channels) {
        listOf("Toutes") + channels.map { it.category }.filter { it.isNotBlank() }.distinct()
    }
    var selectedCategory by rememberSaveable { mutableStateOf("Toutes") }
    var selectedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCategory by remember { mutableStateOf<String?>(null) }
    var pendingChannel by remember { mutableStateOf<LiveChannelUi?>(null) }

    LaunchedEffect(channels) {
        if (selectedChannelId == null || channels.none { it.id == selectedChannelId }) {
            selectedChannelId = channels.firstOrNull()?.id
        }
    }

    val filteredChannels = remember(channels, selectedCategory) {
        if (selectedCategory == "Toutes") channels else channels.filter { it.category == selectedCategory }
    }

    val selectedChannel = channels.firstOrNull { it.id == selectedChannelId }
        ?: filteredChannels.firstOrNull()
        ?: channels.firstOrNull()

    LaunchedEffect(selectedChannel?.id, profile) {
        val channel = selectedChannel ?: return@LaunchedEffect
        if (profile == DeviceProfile.Television && !channel.isLocked) {
            delay(TV_PREVIEW_DELAY_MS)
            onPreviewChannel(channel)
        }
    }

    if (channels.isEmpty()) {
        LiveEmptyState()
        return
    }

    if (profile == DeviceProfile.Mobile) {
        MobileLiveLayout(
            categories = categories,
            selectedCategory = selectedCategory,
            lockedCategories = lockedCategories,
            onCategorySelected = { category ->
                if (category in lockedCategories) {
                    pendingCategory = category
                } else {
                    selectedCategory = category
                    selectedChannelId = channels.firstOrNull { channel ->
                        category == "Toutes" || channel.category == category
                    }?.id
                }
            },
            channels = filteredChannels,
            selectedChannel = selectedChannel,
            onChannelSelected = { selectedChannelId = it.id },
            restoreFocusChannelId = selectedChannelId,
            onTuneChannel = { channel ->
                if (channel.isLocked) pendingChannel = channel else onTuneChannel(channel)
            },
            onOpenGuide = onOpenGuide,
        )
    } else {
        LargeLiveLayout(
            isTelevision = profile == DeviceProfile.Television,
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = { category ->
                if (category in lockedCategories) {
                    pendingCategory = category
                } else {
                    selectedCategory = category
                    selectedChannelId = channels.firstOrNull { channel ->
                        category == "Toutes" || channel.category == category
                    }?.id
                }
            },
            channels = filteredChannels,
            selectedChannel = selectedChannel,
            onChannelSelected = { selectedChannelId = it.id },
            restoreFocusChannelId = selectedChannelId,
            onTuneChannel = onTuneChannel,
            onOpenGuide = onOpenGuide,
        )
    }
}

@Composable
private fun MobileLiveLayout(
    categories: List<String>,
    selectedCategory: String,
    lockedCategories: Set<String>,
    onCategorySelected: (String) -> Unit,
    channels: List<LiveChannelUi>,
    selectedChannel: LiveChannelUi?,
    onChannelSelected: (LiveChannelUi) -> Unit,
    restoreFocusChannelId: String?,
    onTuneChannel: (LiveChannelUi) -> Unit,
    onOpenGuide: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        LiveHeader(onOpenGuide)
        Spacer(Modifier.height(16.dp))
        CategoryRow(categories, selectedCategory, onCategorySelected, false, lockedCategories)
        Spacer(Modifier.height(16.dp))
        PlayerPanel(selectedChannel, onTuneChannel)
        Spacer(Modifier.height(18.dp))
        ChannelList(
            channels = channels,
            selectedChannel = selectedChannel,
            onChannelSelected = onChannelSelected,
            isTelevision = false,
            restoreFocusChannelId = restoreFocusChannelId,
        )
    }
}

@Composable
private fun LargeLiveLayout(
    isTelevision: Boolean,
    categories: List<String>,
    selectedCategory: String,
    lockedCategories: Set<String>,
    onCategorySelected: (String) -> Unit,
    channels: List<LiveChannelUi>,
    selectedChannel: LiveChannelUi?,
    onChannelSelected: (LiveChannelUi) -> Unit,
    restoreFocusChannelId: String?,
    onTuneChannel: (LiveChannelUi) -> Unit,
    onOpenGuide: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LiveHeader(onOpenGuide)
        Spacer(Modifier.height(14.dp))
        CategoryRow(categories, selectedCategory, onCategorySelected, isTelevision, lockedCategories)
        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(
                modifier = Modifier
                    .width(if (isTelevision) 390.dp else 330.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                ChannelList(
                    channels = channels,
                    selectedChannel = selectedChannel,
                    onChannelSelected = onChannelSelected,
                    isTelevision = isTelevision,
                    restoreFocusChannelId = restoreFocusChannelId,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
            ) {
                PlayerPanel(selectedChannel, onTuneChannel)
            }
        }
    }
}

@Composable
private fun LiveHeader(onOpenGuide: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.LiveTv,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "TV en direct",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "Chaînes et programmes en cours",
                color = ZyvioTextSecondary,
            )
        }
        TextButton(onClick = onOpenGuide) {
            Text("Guide TV")
        }
    }
}

@Composable
private fun CategoryRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    isTelevision: Boolean,
    lockedCategories: Set<String>,
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
                    containerColor = if (selected) MaterialTheme.colorScheme.primary else ZyvioSurface1,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (category in lockedCategories) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Verrouillé",
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                    text = category,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerPanel(
    channel: LiveChannelUi?,
    onTuneChannel: (LiveChannelUi) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZyvioSurface1,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(ZyvioSurface2, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = channel?.name ?: "Aucune chaîne sélectionnée",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Sélectionnez Regarder pour lancer le flux.",
                        color = ZyvioTextSecondary,
                    )
                }
            }

            channel?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = it.currentProgram ?: "Programme en cours indisponible",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                it.progress?.let { progress ->
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = ZyvioSurface2,
                    )
                }

                if (!it.nextProgram.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "À suivre : " + it.nextProgram,
                        color = ZyvioTextSecondary,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Button(onClick = { onTuneChannel(it) }) {
                    Text("Regarder")
                }
            }
        }
    }
}

@Composable
private fun ChannelList(
    channels: List<LiveChannelUi>,
    selectedChannel: LiveChannelUi?,
    onChannelSelected: (LiveChannelUi) -> Unit,
    isTelevision: Boolean,
    restoreFocusChannelId: String?,
) {
    val focusRequesters = remember(channels) {
        channels.associate { it.id to FocusRequester() }
    }

    LaunchedEffect(isTelevision, restoreFocusChannelId, channels) {
        if (isTelevision && restoreFocusChannelId != null) {
            focusRequesters[restoreFocusChannelId]?.requestFocus()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        channels.forEach { channel ->
            val selected = channel.id == selectedChannel?.id
            val requester = focusRequesters[channel.id]
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                    .onFocusChanged {
                        if (isTelevision && it.isFocused) {
                            onChannelSelected(channel)
                        }
                    }
                    .tvFocusEffect(isTelevision, cornerRadiusDp = 14)
                    .clickable { onChannelSelected(channel) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) ZyvioRedTint else ZyvioSurface1,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier
                            .width(54.dp)
                            .height(54.dp),
                        color = ZyvioSurface2,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = channel.channelNumber ?: "TV",
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.primary else ZyvioTextSecondary,
                            )
                        }
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
                            text = channel.currentProgram ?: "Guide indisponible",
                            color = ZyvioTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(14.dp))
            Text("Chargement des chaînes…")
        }
    }
}

@Composable
private fun LiveErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = ZyvioSurface1,
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Impossible de charger la TV",
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
private fun LiveEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = ZyvioSurface1,
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Aucune chaîne disponible",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Connectez une playlist réelle pour afficher vos chaînes.",
                    modifier = Modifier.padding(top = 6.dp),
                    color = ZyvioTextSecondary,
                )
            }
        }
    }
}

private const val TV_PREVIEW_DELAY_MS = 600L
