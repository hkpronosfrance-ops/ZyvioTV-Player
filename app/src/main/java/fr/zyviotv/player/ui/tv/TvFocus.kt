package fr.zyviotv.player.ui.tv

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.ui.theme.ZyvioTextPrimary

fun Modifier.tvFocusEffect(
    enabled: Boolean,
    cornerRadiusDp: Int = 16,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(cornerRadiusDp.dp)

    this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            val scale = if (enabled && focused) 1.02f else 1f
            scaleX = scale
            scaleY = scale
            shadowElevation = if (enabled && focused) 16f else 0f
        }
        .border(
            width = if (enabled && focused) 4.dp else 0.dp,
            color = if (enabled && focused) ZyvioTextPrimary else Color.Transparent,
            shape = shape,
        )
}
