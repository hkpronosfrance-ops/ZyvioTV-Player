package fr.zyviotv.player.ui.search

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.shared.catalog.CatalogSnapshot
import fr.zyviotv.player.shared.search.CatalogSearchEngine
import fr.zyviotv.player.shared.search.SearchKind
import fr.zyviotv.player.shared.search.SearchResultItem
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.settings.ParentalUnlockDialog
import fr.zyviotv.player.ui.theme.ZyvioSpace
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioSurface2
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import fr.zyviotv.player.ui.tv.tvFocusEffect
import kotlinx.coroutines.delay

private enum class SearchFilter(val label: String) {
    All("Tout"),
    Live("TV"),
    Movies("Films"),
    Series("Séries"),
}

@Composable
fun SearchScreen(
    profile: DeviceProfile,
    snapshot: CatalogSnapshot = CatalogSnapshot(),
    lockedCategoryKeys: Set<String> = emptySet(),
    lockedContentKeys: Set<String> = emptySet(),
    onBack: () -> Unit,
    onResultSelected: (SearchResultItem) -> Unit = {},
) {
    val context = LocalContext.current
    val recentStore = remember { RecentSearchStore(context.applicationContext) }

    var query by remember { mutableStateOf("") }
    var committedQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SearchFilter.All) }
    var recent by remember { mutableStateOf(recentStore.load()) }
    var pendingResult by remember { mutableStateOf<SearchResultItem?>(null) }

    LaunchedEffect(query) {
        delay(250)
        committedQuery = query.trim()
    }

    val allResults = remember(snapshot, committedQuery) {
        CatalogSearchEngine.search(snapshot, committedQuery)
    }

    val results = remember(allResults, filter) {
        when (filter) {
            SearchFilter.All -> allResults
            SearchFilter.Live -> allResults.filter { it.kind == SearchKind.Live }
            SearchFilter.Movies -> allResults.filter { it.kind == SearchKind.Movie }
            SearchFilter.Series -> allResults.filter { it.kind == SearchKind.Series }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = if (profile == DeviceProfile.Television) ZyvioSpace.s10 else ZyvioSpace.s2,
                vertical = ZyvioSpace.s2,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (profile != DeviceProfile.Television) {
                TextButton(onClick = onBack) {
                    Text("Retour")
                }
                Spacer(Modifier.width(8.dp))
            }

            Text(
                text = "Recherche",
                style = if (profile == DeviceProfile.Television) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.headlineMedium
                },
                fontWeight = FontWeight.ExtraBold,
            )
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Chaîne, film ou série") },
            shape = RoundedCornerShape(14.dp),
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ZyvioSpace.s2),
        ) {
            SearchFilter.entries.forEach { item ->
                FilterChip(
                    selected = filter == item,
                    modifier = Modifier.tvFocusEffect(profile == DeviceProfile.Television, cornerRadiusDp = 999),
                    onClick = { filter = item },
                    label = { Text(item.label) },
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        when {
            committedQuery.length < CatalogSearchEngine.MIN_QUERY_LENGTH -> {
                RecentSearches(
                    recent = recent,
                    onPick = { query = it },
                    onClear = {
                        recentStore.clear()
                        recent = emptyList()
                    },
                )
            }

            results.isEmpty() -> {
                EmptySearchState(hasCatalog = snapshotHasContent(snapshot))
            }

            else -> {
                LaunchedEffect(committedQuery) {
                    recentStore.add(committedQuery)
                    recent = recentStore.load()
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = results,
                        key = { "${it.kind}:${it.id}" },
                    ) { result ->
                        val locked = isSearchResultLocked(
                            snapshot = snapshot,
                            item = result,
                            lockedCategoryKeys = lockedCategoryKeys,
                            lockedContentKeys = lockedContentKeys,
                        )
                        SearchResultRow(
                            item = result,
                            isLocked = locked,
                            isTelevision = profile == DeviceProfile.Television,
                            onClick = {
                                if (locked) pendingResult = result
                                else onResultSelected(result)
                            },
                        )
                    }
                }
            }
        }

        ParentalUnlockDialog(
            visible = pendingResult != null,
            title = "Contenu verrouillé",
            onDismiss = { pendingResult = null },
            onUnlocked = {
                val result = pendingResult
                pendingResult = null
                if (result != null) onResultSelected(result)
            },
        )
    }
}

@Composable
private fun RecentSearches(
    recent: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (recent.isEmpty()) {
        Text(
            text = "Saisissez au moins 2 caractères pour rechercher dans votre catalogue local.",
            color = ZyvioTextSecondary,
            style = MaterialTheme.typography.bodyLarge,
        )
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Recherches récentes",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        TextButton(onClick = onClear) {
            Text("Effacer")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        recent.forEach { value ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(value) },
                color = ZyvioSurface1,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(value)
                }
            }
        }
    }
}

@Composable
private fun EmptySearchState(hasCatalog: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZyvioSurface1,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (hasCatalog) "Aucun résultat" else "Catalogue indisponible",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (hasCatalog) {
                    "Essayez un autre titre, une catégorie ou un numéro de chaîne."
                } else {
                    "La recherche est prête, mais aucun catalogue réel n'est encore connecté à cet écran."
                },
                modifier = Modifier.padding(top = 6.dp),
                color = ZyvioTextSecondary,
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    item: SearchResultItem,
    isLocked: Boolean,
    isTelevision: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusEffect(isTelevision, cornerRadiusDp = 14)
            .clickable(onClick = onClick),
        color = ZyvioSurface1,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .width(if (item.kind == SearchKind.Live) 72.dp else 54.dp)
                    .height(if (item.kind == SearchKind.Live) 46.dp else 72.dp),
                color = ZyvioSurface2,
                shape = RoundedCornerShape(10.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (item.kind) {
                            SearchKind.Live -> Icons.Default.LiveTv
                            SearchKind.Movie -> Icons.Default.Movie
                            SearchKind.Series -> Icons.Default.VideoLibrary
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Verrouillé",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                    text = item.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    )
                }
                val meta = listOfNotNull(
                    when (item.kind) {
                        SearchKind.Live -> "TV"
                        SearchKind.Movie -> "Film"
                        SearchKind.Series -> "Série"
                    },
                    item.channelNumber?.let { "Canal $it" },
                    item.category,
                ).joinToString(" • ")
                Text(
                    text = meta,
                    modifier = Modifier.padding(top = 4.dp),
                    color = ZyvioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun snapshotHasContent(snapshot: CatalogSnapshot): Boolean =
    snapshot.liveChannels.isNotEmpty() || snapshot.movies.isNotEmpty() || snapshot.series.isNotEmpty()

private class RecentSearchStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<String> =
        prefs.getString(KEY, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.take(MAX_RECENT)
            ?: emptyList()

    fun add(value: String) {
        val normalized = value.trim()
        if (normalized.length < CatalogSearchEngine.MIN_QUERY_LENGTH) return
        val next = (listOf(normalized) + load().filterNot { it.equals(normalized, ignoreCase = true) })
            .take(MAX_RECENT)
        prefs.edit().putString(KEY, next.joinToString(SEPARATOR)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val PREFS = "zyviotv_search"
        const val KEY = "recent"
        const val SEPARATOR = "\u001F"
        const val MAX_RECENT = 10
    }
}


private fun isSearchResultLocked(
    snapshot: CatalogSnapshot,
    item: SearchResultItem,
    lockedCategoryKeys: Set<String>,
    lockedContentKeys: Set<String>,
): Boolean {
    val prefix = when (item.kind) {
        SearchKind.Live -> "live"
        SearchKind.Movie -> "movie"
        SearchKind.Series -> "series"
    }
    if (prefix + ":" + item.id in lockedContentKeys) return true

    val categoryId = when (item.kind) {
        SearchKind.Live -> snapshot.liveChannels.firstOrNull { it.id == item.id }?.categoryId
        SearchKind.Movie -> snapshot.movies.firstOrNull { it.id == item.id }?.categoryId
        SearchKind.Series -> snapshot.series.firstOrNull { it.id == item.id }?.categoryId
    }
    return categoryId != null && prefix + ":" + categoryId in lockedCategoryKeys
}
