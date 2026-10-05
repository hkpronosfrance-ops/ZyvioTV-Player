package fr.zyviotv.player.ui.tv

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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

fun Modifier.tvFocusEffect(
    enabled: Boolean,
    cornerRadiusDp: Int = 16,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val focusColor = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(cornerRadiusDp.dp)

    this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            val scale = if (enabled && focused) 1.045f else 1f
            scaleX = scale
            scaleY = scale
        }
        .border(
            width = if (enabled && focused) 2.dp else 0.dp,
            color = if (enabled && focused) focusColor else Color.Transparent,
            shape = shape,
        )
}
