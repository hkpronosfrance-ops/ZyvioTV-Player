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
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, "UTF-8")

        val programmes = ArrayList<EpgProgramme>()
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT && programmes.size < maxProgrammes) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                val programmeChannel = parser.getAttributeValue(null, "channel")
                val startRaw = parser.getAttributeValue(null, "start")
                val stopRaw = parser.getAttributeValue(null, "stop")

                if (programmeChannel == channelId && startRaw != null && stopRaw != null) {
                    val start = parseXmlTvTime(startRaw)
                    val stop = parseXmlTvTime(stopRaw)

                    var title = ""
                    var description: String? = null

                    var inner = parser.next()
                    while (!(inner == XmlPullParser.END_TAG && parser.name == "programme")) {
                        if (inner == XmlPullParser.START_TAG) {
                            when (parser.name) {
                                "title" -> title = parser.nextText()
                                "desc" -> description = parser.nextText()
                            }
                        }
                        inner = parser.next()
                    }

                    if (start != null && stop != null && stop > start) {
                        val programme = EpgProgramme(
                            channelId = channelId,
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

        return programmes.sortedBy { it.startEpochSeconds }
    }

    private fun parseXmlTvTime(raw: String): Long? {
        val normalized = raw.trim()
        val candidates = listOf(
            "yyyyMMddHHmmss Z",
            "yyyyMMddHHmm Z",
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
