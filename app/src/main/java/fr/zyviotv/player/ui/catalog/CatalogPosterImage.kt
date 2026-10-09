package fr.zyviotv.player.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/**
 * Bloc #211: poster of a film or series card in the catalogue grids, or the
 * logo of a channel in the live list.
 *
 * No request is made for a missing or blank URL. The [fallback] (icon and
 * title) stays visible until the image is really displayed, so a slow, broken
 * or refused poster never leaves an empty card. Coil loads the image at the
 * size of the card, not at its original size.
 */
@Composable
fun CatalogPosterImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable BoxScope.() -> Unit,
) {
    val posterUrl = url?.trim()?.takeIf { it.isNotEmpty() }
    var displayed by remember(posterUrl) { mutableStateOf(false) }
    Box(modifier = modifier) {
        if (!displayed) fallback()
        if (posterUrl != null) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onSuccess = { displayed = true },
                onError = { displayed = false },
            )
        }
    }
}
