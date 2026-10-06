package fr.zyviotv.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.shared.catalog.CatalogSnapshot

@Composable
fun ParentalContentLockEditor(
    snapshot: CatalogSnapshot,
    lockedCategoryKeys: Set<String>,
    lockedContentKeys: Set<String>,
    onCategoryLocksChange: (Set<String>) -> Unit,
    onContentLocksChange: (Set<String>) -> Unit,
) {
    var channelQuery by remember { mutableStateOf("") }
    var contentQuery by remember { mutableStateOf("") }

    Spacer(Modifier.height(18.dp))
    Text(
        text = "Verrouillages",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = "${lockedCategoryKeys.size} catégorie(s) · ${lockedContentKeys.size} contenu(s)",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )

    Spacer(Modifier.height(12.dp))
    Text("Catégories", fontWeight = FontWeight.SemiBold)

    val categories = remember(snapshot) {
        buildList {
            snapshot.liveCategories.forEach { add(CategoryLockRow("live", it.id, it.name)) }
            snapshot.movieCategories.forEach { add(CategoryLockRow("movie", it.id, it.name)) }
            snapshot.seriesCategories.forEach { add(CategoryLockRow("series", it.id, it.name)) }
        }.sortedBy { it.name.lowercase() }
    }

    categories.forEach { item ->
        val key = item.kind + ":" + item.id
        val adult = isAdultCategoryLabel(item.name)
        LockToggleRow(
            title = item.name,
            subtitle = when {
                adult -> "Adulte · toujours masquée sur un profil Enfant"
                item.kind == "live" -> "TV"
                item.kind == "movie" -> "Films"
                else -> "Séries"
            },
            checked = adult || key in lockedCategoryKeys,
            enabled = !adult,
            onCheckedChange = { checked ->
                val next = lockedCategoryKeys.toMutableSet()
                if (checked) next.add(key) else next.remove(key)
                onCategoryLocksChange(next)
            },
        )
    }

    Spacer(Modifier.height(16.dp))
    Text("Chaînes verrouillées", fontWeight = FontWeight.SemiBold)
    OutlinedTextField(
        value = channelQuery,
        onValueChange = { channelQuery = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Rechercher une chaîne") },
    )
    val channelResults = remember(snapshot, channelQuery) {
        val q = channelQuery.trim().lowercase()
        if (q.length < 2) emptyList()
        else snapshot.liveChannels
            .filter { it.name.lowercase().contains(q) }
            .take(100)
    }
    channelResults.forEach { channel ->
        val key = "live:" + channel.id
        LockToggleRow(
            title = channel.name,
            subtitle = "Chaîne TV",
            checked = key in lockedContentKeys,
            onCheckedChange = { checked ->
                val next = lockedContentKeys.toMutableSet()
                if (checked) next.add(key) else next.remove(key)
                onContentLocksChange(next)
            },
        )
    }

    Spacer(Modifier.height(16.dp))
    Text("Films & séries verrouillés", fontWeight = FontWeight.SemiBold)
    OutlinedTextField(
        value = contentQuery,
        onValueChange = { contentQuery = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Rechercher un film ou une série") },
    )
    val q = contentQuery.trim().lowercase()
    if (q.length >= 2) {
        snapshot.movies
            .asSequence()
            .filter { it.title.lowercase().contains(q) }
            .take(50)
            .forEach { movie ->
                val key = "movie:" + movie.id
                LockToggleRow(
                    title = movie.title,
                    subtitle = "Film",
                    checked = key in lockedContentKeys,
                    onCheckedChange = { checked ->
                        val next = lockedContentKeys.toMutableSet()
                        if (checked) next.add(key) else next.remove(key)
                        onContentLocksChange(next)
                    },
                )
            }

        snapshot.series
            .asSequence()
            .filter { it.title.lowercase().contains(q) }
            .take(50)
            .forEach { series ->
                val key = "series:" + series.id
                LockToggleRow(
                    title = series.title,
                    subtitle = "Série",
                    checked = key in lockedContentKeys,
                    onCheckedChange = { checked ->
                        val next = lockedContentKeys.toMutableSet()
                        if (checked) next.add(key) else next.remove(key)
                        onContentLocksChange(next)
                    },
                )
            }
    } else {
        Text(
            text = "Saisissez au moins 2 caractères pour rechercher.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LockToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
    Spacer(Modifier.height(8.dp))
}

private data class CategoryLockRow(
    val kind: String,
    val id: String,
    val name: String,
)

private fun isAdultCategoryLabel(value: String): Boolean {
    val normalized = value
        .lowercase()
        .replace("é", "e")
        .replace("è", "e")
        .replace("ê", "e")
        .replace("à", "a")
        .replace("â", "a")
        .replace("î", "i")
        .replace("ï", "i")
        .replace("ô", "o")
        .replace("ù", "u")
        .replace("û", "u")

    return listOf(
        "adult",
        "adulte",
        "xxx",
        "porn",
        "erotic",
        "erotique",
        "18+",
        "+18",
    ).any(normalized::contains)
}
