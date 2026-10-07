package fr.zyviotv.player.data.settings

import android.content.Context
import android.os.SystemClock
import java.util.Calendar
import java.util.TimeZone
import org.json.JSONArray

data class CachedParentalRuntime(
    val parentalEnabled: Boolean,
    val isChild: Boolean,
    val scheduleEnabled: Boolean,
    val scheduleWindowsJson: String,
    val trustedEpochMillis: Long,
    val trustedElapsedRealtime: Long,
    val consumedSeconds: Int,
    val usageDayUtc: String,
    val dailyLimitMinutes: Int?,
    val weekendLimitMinutes: Int?,
)

class ParentalRuntimeCache(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun store(profileId: String, state: ParentalRuntimeState) {
        val epoch = state.serverNowEpochMillis ?: return
        val day = utcDayKey(epoch)
        val existing = load(profileId)
        val consumed = if (existing != null && existing.usageDayUtc == day) {
            maxOf(existing.consumedSeconds, state.consumedSeconds.coerceAtLeast(0))
        } else {
            state.consumedSeconds.coerceAtLeast(0)
        }

        preferences.edit()
            .putBoolean(key(profileId, "enabled"), state.parentalEnabled)
            .putBoolean(key(profileId, "child"), state.isChild)
            .putBoolean(key(profileId, "schedule_enabled"), state.scheduleEnabled)
            .putString(key(profileId, "schedule_windows"), state.scheduleWindowsJson)
            .putLong(key(profileId, "trusted_epoch"), epoch)
            .putLong(key(profileId, "trusted_elapsed"), SystemClock.elapsedRealtime())
            .putInt(key(profileId, "consumed_seconds"), consumed)
            .putString(key(profileId, "usage_day_utc"), day)
            .putNullableInt(key(profileId, "daily_limit_minutes"), state.dailyLimitMinutes)
            .putNullableInt(key(profileId, "weekend_limit_minutes"), state.weekendLimitMinutes)
            .apply()
    }

    fun load(profileId: String): CachedParentalRuntime? {
        if (!preferences.contains(key(profileId, "trusted_epoch"))) return null
        return CachedParentalRuntime(
            parentalEnabled = preferences.getBoolean(key(profileId, "enabled"), false),
            isChild = preferences.getBoolean(key(profileId, "child"), false),
            scheduleEnabled = preferences.getBoolean(key(profileId, "schedule_enabled"), false),
            scheduleWindowsJson = preferences.getString(
                key(profileId, "schedule_windows"),
                "[]",
            ) ?: "[]",
            trustedEpochMillis = preferences.getLong(key(profileId, "trusted_epoch"), 0L),
            trustedElapsedRealtime = preferences.getLong(key(profileId, "trusted_elapsed"), 0L),
            consumedSeconds = preferences.getInt(key(profileId, "consumed_seconds"), 0),
            usageDayUtc = preferences.getString(
                key(profileId, "usage_day_utc"),
                "",
            ).orEmpty(),
            dailyLimitMinutes = preferences.getNullableInt(
                key(profileId, "daily_limit_minutes"),
            ),
            weekendLimitMinutes = preferences.getNullableInt(
                key(profileId, "weekend_limit_minutes"),
            ),
        )
    }

    fun updateConsumedSeconds(profileId: String, consumedSeconds: Int) {
        val cached = load(profileId) ?: return
        val currentDay = utcDayKey(ParentalScheduleEvaluator.trustedNowMillis(cached))
        val value = if (cached.usageDayUtc == currentDay) {
            maxOf(cached.consumedSeconds, consumedSeconds.coerceAtLeast(0))
        } else {
            consumedSeconds.coerceAtLeast(0)
        }
        preferences.edit()
            .putInt(key(profileId, "consumed_seconds"), value)
            .putString(key(profileId, "usage_day_utc"), currentDay)
            .apply()
    }

    fun addOfflineSeconds(profileId: String, seconds: Int): Int {
        val cached = load(profileId) ?: return 0
        val currentDay = utcDayKey(ParentalScheduleEvaluator.trustedNowMillis(cached))
        val base = if (cached.usageDayUtc == currentDay) cached.consumedSeconds else 0
        val next = (base + seconds.coerceAtLeast(0)).coerceAtMost(Int.MAX_VALUE)
        preferences.edit()
            .putInt(key(profileId, "consumed_seconds"), next)
            .putString(key(profileId, "usage_day_utc"), currentDay)
            .apply()
        return next
    }

    fun consumedSeconds(profileId: String): Int {
        val cached = load(profileId) ?: return 0
        val currentDay = utcDayKey(ParentalScheduleEvaluator.trustedNowMillis(cached))
        return if (cached.usageDayUtc == currentDay) cached.consumedSeconds else 0
    }

    fun clear(profileId: String) {
        preferences.edit()
            .remove(key(profileId, "enabled"))
            .remove(key(profileId, "child"))
            .remove(key(profileId, "schedule_enabled"))
            .remove(key(profileId, "schedule_windows"))
            .remove(key(profileId, "trusted_epoch"))
            .remove(key(profileId, "trusted_elapsed"))
            .remove(key(profileId, "consumed_seconds"))
            .remove(key(profileId, "usage_day_utc"))
            .remove(key(profileId, "daily_limit_minutes"))
            .remove(key(profileId, "weekend_limit_minutes"))
            .apply()
    }

    fun effectiveLimitMinutes(profileId: String): Int? {
        val cached = load(profileId) ?: return null
        val now = ParentalScheduleEvaluator.trustedNowMillis(cached)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = now
        }
        val weekend = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
            calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        return if (weekend) {
            cached.weekendLimitMinutes ?: cached.dailyLimitMinutes
        } else {
            cached.dailyLimitMinutes
        }
    }

    private fun android.content.SharedPreferences.Editor.putNullableInt(
        key: String,
        value: Int?,
    ): android.content.SharedPreferences.Editor =
        if (value == null) remove(key) else putInt(key, value)

    private fun android.content.SharedPreferences.getNullableInt(key: String): Int? =
        if (contains(key)) getInt(key, 0) else null

    private fun key(profileId: String, suffix: String): String = profileId + "_" + suffix

    private companion object {
        const val PREFS_NAME = "zyviotv_parental_runtime_cache"
    }
}

private fun utcDayKey(epochMillis: Long): String {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = epochMillis
    }
    return "%04d-%02d-%02d".format(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
    )
}

object ParentalScheduleEvaluator {
    /**
     * Schedule JSON schema:
     * [
     *   {"days":[1,2,3,4,5],"start":"16:30","end":"19:30"},
     *   {"days":[6,7],"start":"09:00","end":"12:00"}
     * ]
     * days use ISO values: Monday=1 ... Sunday=7.
     */
    fun isAllowedNow(
        cached: CachedParentalRuntime,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Boolean {
        if (!cached.parentalEnabled || !cached.isChild || !cached.scheduleEnabled) {
            return true
        }

        val nowMillis = trustedNowMillis(cached)
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
        }
        val isoDay = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
        val previousIsoDay = if (isoDay == 1) 7 else isoDay - 1
        val minuteOfDay =
            calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        val windows = runCatching { JSONArray(cached.scheduleWindowsJson) }.getOrNull()
            ?: return false

        if (windows.length() == 0) return false

        for (index in 0 until windows.length()) {
            val window = windows.optJSONObject(index) ?: continue
            val days = window.optJSONArray("days") ?: continue
            val start = parseMinute(window.optString("start")) ?: continue
            val end = parseMinute(window.optString("end")) ?: continue

            if (start == end) {
                if (containsDay(days, isoDay)) return true
                continue
            }

            if (start < end) {
                if (containsDay(days, isoDay) && minuteOfDay in start until end) {
                    return true
                }
            } else {
                if (containsDay(days, isoDay) && minuteOfDay >= start) {
                    return true
                }
                if (containsDay(days, previousIsoDay) && minuteOfDay < end) {
                    return true
                }
            }
        }

        return false
    }

    fun trustedNowMillis(cached: CachedParentalRuntime): Long {
        val elapsedDelta =
            (SystemClock.elapsedRealtime() - cached.trustedElapsedRealtime).coerceAtLeast(0L)
        return cached.trustedEpochMillis + elapsedDelta
    }

    private fun containsDay(days: JSONArray, isoDay: Int): Boolean {
        for (index in 0 until days.length()) {
            if (days.optInt(index, -1) == isoDay) return true
        }
        return false
    }

    private fun parseMinute(value: String): Int? {
        val parts = value.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }
}
