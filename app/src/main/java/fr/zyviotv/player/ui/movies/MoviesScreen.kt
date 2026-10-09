package fr.zyviotv.player.ui.movies

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.ui.catalog.CatalogPosterImage
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.data.catalog.CatalogPerformanceDiagnostics
import fr.zyviotv.player.ui.catalog.catalogPosterMinimumWidth
import fr.zyviotv.player.ui.settings.ParentalUnlockDialog
import fr.zyviotv.player.ui.theme.ZyvioSpace
import fr.zyviotv.player.ui.theme.ZyvioRedTint
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioSurface2
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import fr.zyviotv.player.ui.theme.ZyvioTextTertiary
import fr.zyviotv.player.ui.tv.tvFocusEffect
import kotlinx.coroutines.yield

data class MovieCatalogItem(
    val id: String,
    val title: String,
    val year: String? = null,
    val rating: String? = null,
    val category: String,
    val posterUrl: String? = null,
    val progress: Float? = null,
    val isNew: Boolean = false,
    val isLocked: Boolean = false,
    /** Precomputed A-Z key of the display title (bloc #211). */
    val sortKey: String = title.lowercase(),
)

sealed interface MoviesScreenState {
    data object Loading : MoviesScreenState
    data class Ready(
        val items: List<MovieCatalogItem>,
        val categories: List<String>,
        val lockedCategories: Set<String> = emptySet(),
    ) : MoviesScreenState
    data class Error(val message: String) : MoviesScreenState
}

@Composable
fun MoviesScreen(
    profile: DeviceProfile,
    state: MoviesScreenState = MoviesScreenState.Ready(emptyList(), emptyList()),
    onRetry: () -> Unit = {},
    onMovieSelected: (MovieCatalogItem) -> Unit = {},
) {
    when (state) {
        MoviesScreenState.Loading -> MoviesLoading(profile)
        is MoviesScreenState.Error -> MoviesError(state.message, onRetry)
        is MoviesScreenState.Ready -> MoviesReady(
            profile = profile,
            items = state.items,
            categories = state.categories,
            lockedCategories = state.lockedCategories,
            onMovieSelected = onMovieSelected,
        )
    }
}

@Composable
private fun MoviesReady(
    profile: DeviceProfile,
    items: List<MovieCatalogItem>,
    categories: List<String>,
    lockedCategories: Set<String>,
    onMovieSelected: (MovieCatalogItem) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val tabletLandscape = profile == DeviceProfile.Tablet &&
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val allCategories = remember(categories, items) {
        (listOf("Toutes", "Nouveautés") + categories + items.map { it.category })
            .filter { it.isNotBlank() }
            .distinct()
    }
    var selectedCategory by rememberSaveable { mutableStateOf("Toutes") }
    var sort by rememberSaveable { mutableStateOf("Popularité") }
    var lastSelectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCategory by remember { mutableStateOf<String?>(null) }
    var pendingMovie by remember { mutableStateOf<MovieCatalogItem?>(null) }

    val filtered = remember(items, selectedCategory, sort) {
        val startedAt = CatalogPerformanceDiagnostics.startedAt()
        val base = when (selectedCategory) {
            "Toutes" -> items
            "Nouveautés" -> items.filter { it.isNew }
            else -> items.filter { it.category == selectedCategory }
        }
        val result = when (sort) {
            "A-Z" -> base.sortedBy { it.sortKey }
            "Année" -> base.sortedByDescending { it.year ?: "" }
            "Note" -> base.sortedByDescending { it.rating?.replace(",", ".")?.toDoubleOrNull() ?: -1.0 }
            else -> base
        }
        CatalogPerformanceDiagnostics.phase("open_movie_category", startedAt, result.size)
        result
    }

    Column(Modifier.fillMaxSize()) {
        CatalogHeader(
            title = "Films",
            count = filtered.size,
            sort = sort,
            onSort = {
                sort = when (sort) {
                    "Popularité" -> "Ajout récent"
                    "Ajout récent" -> "Année"
                    "Année" -> "Note"
                    "Note" -> "A-Z"
                    else -> "Popularité"
                }
            },
        )

        Spacer(Modifier.height(14.dp))

        if (tabletLandscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CategoryPanel(
                    categories = allCategories,
                    lockedCategories = lockedCategories,
                    selected = selectedCategory,
                    onSelect = { category ->
                        if (category in lockedCategories) pendingCategory = category
                        else selectedCategory = category
                    },
                    modifier = Modifier.width(220.dp),
                    isTelevision = false,
                )
                MoviesBody(
                    modifier = Modifier.weight(1f),
                    profile = profile,
                    items = filtered,
                    hasFilters = selectedCategory != "Toutes",
                    onReset = { selectedCategory = "Toutes" },
                    lastSelectedId = lastSelectedId,
                    onMovieSelected = {
                        lastSelectedId = it.id
                        if (it.isLocked) pendingMovie = it else onMovieSelected(it)
                    },
                )
            }
        } else {
            CategoryRow(
                categories = allCategories,
                lockedCategories = lockedCategories,
                selected = selectedCategory,
                onSelect = { category ->
                    if (category in lockedCategories) pendingCategory = category
                    else selectedCategory = category
                },
                isTelevision = profile == DeviceProfile.Television,
            )
            Spacer(Modifier.height(14.dp))
            MoviesBody(
                modifier = Modifier.fillMaxSize(),
                profile = profile,
                items = filtered,
                hasFilters = selectedCategory != "Toutes",
                onReset = { selectedCategory = "Toutes" },
                lastSelectedId = lastSelectedId,
                    onMovieSelected = {
                        lastSelectedId = it.id
                        if (it.isLocked) pendingMovie = it else onMovieSelected(it)
                    },
            )
        }

        ParentalUnlockDialog(
            visible = pendingCategory != null,
            title = "Catégorie verrouillée",
            onDismiss = { pendingCategory = null },
            onUnlocked = {
                selectedCategory = pendingCategory ?: selectedCategory
                pendingCategory = null
            },
        )

        ParentalUnlockDialog(
            visible = pendingMovie != null,
            title = "Film verrouillé",
            onDismiss = { pendingMovie = null },
            onUnlocked = {
                val movie = pendingMovie ?: return@ParentalUnlockDialog
                pendingMovie = null
                onMovieSelected(movie)
            },
        )
    }
}

@Composable
private fun MoviesBody(
    modifier: Modifier,
    profile: DeviceProfile,
    items: List<MovieCatalogItem>,
    hasFilters: Boolean,
    onReset: () -> Unit,
    lastSelectedId: String?,
    onMovieSelected: (MovieCatalogItem) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyMoviesState(
            modifier = modifier,
            filtered = hasFilters,
            onReset = onReset,
        )
        return
    }

    val posterMinWidth = catalogPosterMinimumWidth(profile)

    val gridState = rememberLazyGridState()
    val restoreRequester = remember { FocusRequester() }

    LaunchedEffect(profile, lastSelectedId, items) {
        if (profile == DeviceProfile.Television && lastSelectedId != null) {
            val index = items.indexOfFirst { it.id == lastSelectedId }
            if (index >= 0) {
                gridState.scrollToItem(index)
                yield()
                runCatching { restoreRequester.requestFocus() }
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = posterMinWidth),
        state = gridState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (profile == DeviceProfile.Mobile) ZyvioSpace.s2 else ZyvioSpace.s3),
        verticalArrangement = Arrangement.spacedBy(ZyvioSpace.s4),
    ) {
        items(
            items = items,
            key = { it.id },
        ) { movie ->
            MovieCard(
                item = movie,
                isTelevision = profile == DeviceProfile.Television,
                focusRequester = restoreRequester.takeIf { movie.id == lastSelectedId },
                onClick = { onMovieSelected(movie) },
            )
        }
    }
}

@Composable
private fun CatalogHeader(
    title: String,
    count: Int,
    sort: String,
    onSort: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = "  $count",
            color = ZyvioTextTertiary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onSort) {
            Icon(Icons.Default.Sort, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(sort)
        }
    }
}

@Composable
private fun CategoryRow(
    categories: List<String>,
    lockedCategories: Set<String>,
    selected: String,
    onSelect: (String) -> Unit,
    isTelevision: Boolean,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listItems(categories, key = { it }) { category ->
            FilterChip(
                modifier = Modifier.tvFocusEffect(isTelevision, cornerRadiusDp = 999),
                selected = category == selected,
                onClick = { onSelect(category) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (category in lockedCategories) {
                            Icon(Icons.Default.Lock, contentDescription = "Verrouillé")
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
            )
        }
    }
}

@Composable
private fun CategoryPanel(
    categories: List<String>,
    lockedCategories: Set<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier,
    isTelevision: Boolean,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listItems(categories, key = { it }) { category ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusEffect(isTelevision, cornerRadiusDp = 10)
                    .clickable { onSelect(category) },
                color = if (category == selected) ZyvioRedTint else ZyvioSurface1,
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (category in lockedCategories) {
                        Icon(Icons.Default.Lock, contentDescription = "Verrouillé")
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                    text = category,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (category == selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieCard(
    item: MovieCatalogItem,
    isTelevision: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .tvFocusEffect(isTelevision, cornerRadiusDp = 10)
            .clickable(onClick = onClick),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            colors = CardDefaults.cardColors(containerColor = ZyvioSurface1),
            shape = RoundedCornerShape(ZyvioSpace.s3),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ZyvioSurface2),
            ) {
                CatalogPosterImage(
                    url = item.posterUrl,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = ZyvioTextTertiary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = item.title,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (item.isLocked) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Verrouillé",
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                }

                if (item.isNew) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = "NOUVEAU",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }

                item.progress?.takeIf { it > 0f }?.let { progress ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }

        Text(
            text = item.title,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
        )

        val meta = listOfNotNull(
            item.year,
            item.rating?.let { "★ $it" },
        ).joinToString(" • ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                color = ZyvioTextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EmptyMoviesState(
    modifier: Modifier,
    filtered: Boolean,
    onReset: () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.FilterListOff,
                contentDescription = null,
                tint = ZyvioTextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (filtered) "Aucun film ne correspond" else "Aucun film disponible",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (filtered) {
                    "Retirez un filtre ou choisissez une autre catégorie."
                } else {
                    "Votre fournisseur ne propose aucun film pour le moment."
                },
                modifier = Modifier.padding(top = 6.dp),
                color = ZyvioTextSecondary,
            )
            if (filtered) {
                Spacer(Modifier.height(14.dp))
                Button(onClick = onReset) {
                    Text("Réinitialiser les filtres")
                }
            }
        }
    }
}

@Composable
private fun MoviesLoading(profile: DeviceProfile) {
    val posterMinWidth = catalogPosterMinimumWidth(profile)

    Column(Modifier.fillMaxSize()) {
        CatalogHeader(
            title = "Films",
            count = 0,
            sort = "Popularité",
            onSort = {},
        )
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = posterMinWidth),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(ZyvioSpace.s4),
        ) {
            items(12) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .background(ZyvioSurface2, RoundedCornerShape(10.dp)),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(10.dp)
                            .background(ZyvioSurface2, RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoviesError(
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
                CircularProgressIndicator(
                    progress = { 1f },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = ZyvioSurface2,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Catalogue indisponible",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = message,
                    modifier = Modifier.padding(top = 6.dp),
                    color = ZyvioTextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Réessayer")
                }
            }
        }
    }
}
