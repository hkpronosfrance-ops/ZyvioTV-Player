package fr.zyviotv.player.data.epg

import fr.zyviotv.player.shared.epg.EpgProgramme
import fr.zyviotv.player.shared.epg.EpgWindow
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

object XmlTvParser {
    fun parse(
        input: InputStream,
        channelId: String,
        window: EpgWindow,
        maxProgrammes: Int = 500,
    ): List<EpgProgramme> {
        if (maxProgrammes <= 0 || channelId.isBlank()) return emptyList()

        val normalizedChannelId = channelId.trim()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, "UTF-8")

        val programmes = ArrayList<EpgProgramme>()
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT && programmes.size < maxProgrammes) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                val programmeChannel = parser.getAttributeValue(null, "channel")
                val startRaw = parser.getAttributeValue(null, "start")
                val stopRaw = parser.getAttributeValue(null, "stop")

                if (programmeChannel?.trim() == normalizedChannelId && startRaw != null && stopRaw != null) {
                    val start = parseXmlTvTime(startRaw)
                    val stop = parseXmlTvTime(stopRaw)

                    var title = ""
                    var description: String? = null

                    var inner = parser.next()
                    while (!(inner == XmlPullParser.END_TAG && parser.name == "programme")) {
                        if (inner == XmlPullParser.START_TAG) {
                            when (parser.name) {
                                "title" -> title = parser.nextText().trim()
                                "desc" -> description = parser.nextText().trim()
                            }
                        }
                        inner = parser.next()
                    }

                    if (start != null && stop != null && stop > start) {
                        val programme = EpgProgramme(
                            channelId = normalizedChannelId,
                            title = title.ifBlank { "Programme TV" },
                            description = description?.takeIf(String::isNotBlank),
                            startEpochSeconds = start,
                            endEpochSeconds = stop,
                        )
                        if (window.contains(programme)) programmes += programme
                    }
                }
            }
            event = parser.next()
        }

        return programmes
            .distinctBy {
                Triple(it.startEpochSeconds, it.endEpochSeconds, it.title)
            }
            .sortedBy { it.startEpochSeconds }
    }

    private fun parseXmlTvTime(raw: String): Long? {
        val normalized = raw
            .trim()
            .replace(Regex("""(\d{12,14})([+-]\d{4})$"""), "$1 $2")

        val candidates = listOf(
            "yyyyMMddHHmmss Z",
            "yyyyMMddHHmm Z",
            "yyyyMMddHHmmssX",
            "yyyyMMddHHmmX",
        )

        for (pattern in candidates) {
            try {
                val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                }
                return formatter.parse(normalized)?.time?.div(1000L)
            } catch (_: Exception) {
                Unit
            }
        }
        return null
    }
}
