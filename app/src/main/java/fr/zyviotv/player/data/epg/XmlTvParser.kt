package fr.zyviotv.player.data.epg

import fr.zyviotv.player.shared.epg.EpgProgramme
import fr.zyviotv.player.shared.epg.EpgWindow
import java.io.InputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

object XmlTvParser {
    fun parse(
        input: InputStream,
        channelId: String,
        window: EpgWindow,
        maxProgrammes: Int = 500,
    ): List<EpgProgramme> =
        parseChannels(
            input = input,
            channelIds = setOf(channelId),
            window = window,
            maxProgrammes = maxProgrammes,
        )[channelId.trim()].orEmpty()

    fun parseChannels(
        input: InputStream,
        channelIds: Set<String>,
        window: EpgWindow,
        maxProgrammes: Int = 5_000,
    ): Map<String, List<EpgProgramme>> {
        if (maxProgrammes <= 0) return emptyMap()

        val normalizedIds = channelIds
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
        if (normalizedIds.isEmpty()) return emptyMap()

        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, "UTF-8")

        val programmesByChannel = normalizedIds.associateWith {
            mutableListOf<EpgProgramme>()
        }
        var accepted = 0
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT && accepted < maxProgrammes) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                val programmeChannel = parser.getAttributeValue(null, "channel")?.trim()
                val startRaw = parser.getAttributeValue(null, "start")
                val stopRaw = parser.getAttributeValue(null, "stop")

                if (
                    programmeChannel != null &&
                    programmeChannel in normalizedIds &&
                    startRaw != null &&
                    stopRaw != null
                ) {
                    val start = EpgTimeParsing.xmlTvEpochSeconds(startRaw)
                    val stop = EpgTimeParsing.xmlTvEpochSeconds(stopRaw)

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
                            channelId = programmeChannel,
                            title = title.ifBlank { "Programme TV" },
                            description = description?.takeIf(String::isNotBlank),
                            startEpochSeconds = start,
                            endEpochSeconds = stop,
                        )
                        if (window.contains(programme)) {
                            programmesByChannel.getValue(programmeChannel) += programme
                            accepted += 1
                        }
                    }
                }
            }
            event = parser.next()
        }

        return programmesByChannel.mapValues { (_, programmes) ->
            programmes
                .distinctBy {
                    Triple(it.startEpochSeconds, it.endEpochSeconds, it.title)
                }
                .sortedBy { it.startEpochSeconds }
        }
    }
}
