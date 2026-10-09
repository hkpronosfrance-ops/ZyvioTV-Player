package fr.zyviotv.player.data.catalog

/**
 * Bloc #211: stable, unique M3U content ids.
 *
 * M3U entries have no provider id, so ids are a 32-bit hash of their content
 * (`String.hashCode`). Two different entries can share one, which gave
 * duplicate keys to the Compose grids and shared favourites, history and
 * resume points between unrelated titles.
 *
 * Rules:
 * - an id held by a single entry is kept unchanged (favourites, history and
 *   resume points saved with it stay attached);
 * - when several entries share an id, one keeps it: the entry recorded in the
 *   alias table (legacy id to 64-bit content fingerprint) from the previous
 *   import, otherwise the smallest fingerprint, so the choice never depends on
 *   playlist order;
 * - the others get a deterministic secondary id derived from their own
 *   fingerprint (`m3u-` + 16 hex digits, never the 8-digit legacy form).
 */
object M3uIdResolver {
    class Resolution(
        /** Index in the input list to its new id; only entries whose id changes. */
        val replacements: Map<Int, String>,
        /** Legacy id to the fingerprint of the entry that keeps it. */
        val aliases: Map<String, Long>,
    )

    fun resolve(
        legacyIds: List<String>,
        fingerprintOf: (Int) -> Long,
        incumbents: Map<String, Long>,
    ): Resolution {
        val groups = sharedIdGroups(legacyIds)
        if (groups.isEmpty()) return Resolution(emptyMap(), emptyMap())

        val replacements = HashMap<Int, String>()
        val aliases = LinkedHashMap<String, Long>()
        groups.forEach { members ->
            val legacyId = legacyIds[members.first()]
            val fingerprints = members.associateWith(fingerprintOf)
            val recorded = incumbents[legacyId]
            val holder = members.firstOrNull { fingerprints.getValue(it) == recorded }
                ?: members.minWith(
                    compareBy<Int> { fingerprints.getValue(it).toULong() }.thenBy { it },
                )
            aliases[legacyId] = fingerprints.getValue(holder)
            val occurrences = HashMap<Long, Int>()
            members.forEach { index ->
                if (index == holder) return@forEach
                val fingerprint = fingerprints.getValue(index)
                val occurrence = (occurrences[fingerprint] ?: 0) + 1
                occurrences[fingerprint] = occurrence
                replacements[index] = secondaryId(fingerprint, occurrence)
            }
        }
        return Resolution(replacements, aliases)
    }

    /** 64-bit FNV-1a over the parts, separated so ("ab","c") != ("a","bc"). */
    fun fingerprint(vararg parts: String?): Long {
        var hash = FNV_OFFSET_BASIS
        parts.forEach { part ->
            val value = part.orEmpty()
            for (index in value.indices) {
                hash = (hash xor value[index].code.toLong()) * FNV_PRIME
            }
            hash = (hash xor SEPARATOR) * FNV_PRIME
        }
        return hash
    }

    fun secondaryId(fingerprint: Long, occurrence: Int): String {
        val base = "m3u-" + fingerprint.toULong().toString(16).padStart(16, '0')
        return if (occurrence <= 1) base else "$base-$occurrence"
    }

    /**
     * Groups of indices sharing an id, in input order. Open addressing over
     * the ids' own hashes: no map entry per item for 100k+ episodes.
     */
    internal fun sharedIdGroups(ids: List<String>): List<List<Int>> {
        if (ids.size < 2) return emptyList()
        var capacity = 16
        while (capacity < ids.size * 2) capacity = capacity shl 1
        val mask = capacity - 1
        val table = IntArray(capacity) { -1 }
        val groups = LinkedHashMap<Int, MutableList<Int>>()
        ids.forEachIndexed { index, id ->
            var slot = mix(id.hashCode()) and mask
            while (true) {
                val occupant = table[slot]
                if (occupant == -1) {
                    table[slot] = index
                    break
                }
                if (ids[occupant] == id) {
                    groups.getOrPut(occupant) { mutableListOf(occupant) }.add(index)
                    break
                }
                slot = (slot + 1) and mask
            }
        }
        return groups.values.toList()
    }

    private fun mix(value: Int): Int {
        val spread = value * -0x61c88647
        return spread xor (spread ushr 16)
    }

    private const val FNV_OFFSET_BASIS = -0x340d631b7bdddcdbL
    private const val FNV_PRIME = 0x100000001b3L
    private const val SEPARATOR = 0x1FL
}
