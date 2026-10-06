package fr.zyviotv.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

data class ParentalScheduleWindowUi(
    val days: Set<Int>,
    val start: String,
    val end: String,
)

fun parseScheduleWindows(json: String): List<ParentalScheduleWindowUi> = runCatching {
    val array = JSONArray(json)
    buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val daysJson = item.optJSONArray("days") ?: JSONArray()
            val days = buildSet {
                for (dayIndex in 0 until daysJson.length()) {
                    val day = daysJson.optInt(dayIndex, -1)
                    if (day in 1..7) add(day)
                }
            }
            val start = item.optString("start")
            val end = item.optString("end")
            if (days.isNotEmpty() && isValidTime(start) && isValidTime(end)) {
                add(
                    ParentalScheduleWindowUi(
                        days = days,
                        start = start,
                        end = end,
                    ),
                )
            }
        }
    }
}.getOrDefault(emptyList())

fun encodeScheduleWindows(windows: List<ParentalScheduleWindowUi>): String {
    val array = JSONArray()
    windows.forEach { window ->
        val days = JSONArray()
        window.days.sorted().forEach(days::put)
        array.put(
            JSONObject()
                .put("days", days)
                .put("start", window.start)
                .put("end", window.end),
        )
    }
    return array.toString()
}

fun validateScheduleWindows(
    windows: List<ParentalScheduleWindowUi>,
): String? {
    if (windows.isEmpty()) return "Ajoutez au moins une plage horaire."
    windows.forEachIndexed { index, window ->
        if (window.days.isEmpty()) {
            return "La plage ${index + 1} doit contenir au moins un jour."
        }
        if (!isValidTime(window.start) || !isValidTime(window.end)) {
            return "La plage ${index + 1} contient une heure invalide."
        }
        if (window.start == window.end) {
            return "La plage ${index + 1} doit avoir une heure de début différente de la fin."
        }
    }
    return null
}

@Composable
fun ParentalScheduleEditor(
    windows: List<ParentalScheduleWindowUi>,
    enabled: Boolean,
    onChange: (List<ParentalScheduleWindowUi>) -> Unit,
) {
    if (!enabled) return

    Spacer(Modifier.height(10.dp))
    Text("Plages autorisées")

    windows.forEachIndexed { index, window ->
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth()) {
            Text("Plage ${index + 1}")

            Spacer(Modifier.height(6.dp))
            DAY_LABELS.chunked(4).forEachIndexed { rowIndex, labels ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    labels.forEachIndexed { itemIndex, label ->
                        val day = rowIndex * 4 + itemIndex + 1
                        FilterChip(
                            selected = day in window.days,
                            onClick = {
                                val nextDays = window.days.toMutableSet().apply {
                                    if (!add(day)) remove(day)
                                }
                                onChange(
                                    windows.toMutableList().also {
                                        it[index] = window.copy(days = nextDays)
                                    },
                                )
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = window.start,
                    onValueChange = { value ->
                        onChange(
                            windows.toMutableList().also {
                                it[index] = window.copy(start = sanitizeTime(value))
                            },
                        )
                    },
                    label = { Text("Début") },
                    placeholder = { Text("16:30") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = window.end,
                    onValueChange = { value ->
                        onChange(
                            windows.toMutableList().also {
                                it[index] = window.copy(end = sanitizeTime(value))
                            },
                        )
                    },
                    label = { Text("Fin") },
                    placeholder = { Text("19:30") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    onChange(windows.toMutableList().also { it.removeAt(index) })
                },
            ) {
                Text("Supprimer la plage")
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Button(
        enabled = windows.size < MAX_WINDOWS,
        onClick = {
            onChange(
                windows + ParentalScheduleWindowUi(
                    days = setOf(1, 2, 3, 4, 5),
                    start = "16:30",
                    end = "19:30",
                ),
            )
        },
    ) {
        Text("Ajouter une plage")
    }
}

private fun sanitizeTime(value: String): String =
    value.filter { it.isDigit() || it == ':' }.take(5)

private fun isValidTime(value: String): Boolean {
    val parts = value.split(":")
    if (parts.size != 2) return false
    val hour = parts[0].toIntOrNull() ?: return false
    val minute = parts[1].toIntOrNull() ?: return false
    return hour in 0..23 && minute in 0..59 &&
        parts[0].length == 2 && parts[1].length == 2
}

private val DAY_LABELS = listOf("L", "M", "M", "J", "V", "S", "D")
private const val MAX_WINDOWS = 8
