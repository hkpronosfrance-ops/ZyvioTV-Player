package fr.zyviotv.player.shared.catalog

import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayTitleTest {
    @Test
    fun removesTechnicalMarkersAtTheEdges() {
        assertEquals("Le Grand Voyage", DisplayTitle.clean("Le Grand Voyage (MULTI) FHD"))
        assertEquals("Le Grand Voyage", DisplayTitle.clean("[4K] Le Grand Voyage"))
        assertEquals("Le Grand Voyage", DisplayTitle.clean("FR | Le Grand Voyage"))
        assertEquals("Le Grand Voyage", DisplayTitle.clean("Le Grand Voyage - VOSTFR"))
        assertEquals("Le Grand Voyage", DisplayTitle.clean("Le Grand Voyage [HEVC x265]"))
        assertEquals("Le Grand Voyage", DisplayTitle.clean("Le Grand Voyage UHD 4K"))
        assertEquals("Le Grand Voyage (2021)", DisplayTitle.clean("Le Grand Voyage (2021) (VF) HD"))
    }

    @Test
    fun keepsOrdinaryWordsThatLookLikeMarkers() {
        assertEquals("En attendant la nuit", DisplayTitle.clean("En attendant la nuit"))
        assertEquals("French Kiss", DisplayTitle.clean("French Kiss"))
        assertEquals("Le Web des Araignées", DisplayTitle.clean("Le Web des Araignées"))
        assertEquals("Charlotte's Web", DisplayTitle.clean("Charlotte's Web"))
        assertEquals("Mission: Fin", DisplayTitle.clean("Mission: Fin"))
        assertEquals("Spider-Man", DisplayTitle.clean("Spider-Man"))
    }

    @Test
    fun neverRemovesMarkersInsideTheTitle() {
        assertEquals("Le 4K Club des Amis", DisplayTitle.clean("Le 4K Club des Amis"))
        assertEquals("Avant (MULTI) Après", DisplayTitle.clean("Avant (MULTI) Après"))
    }

    @Test
    fun neverReturnsAnEmptyTitle() {
        assertEquals("4K", DisplayTitle.clean("4K"))
        assertEquals("(MULTI) FHD", DisplayTitle.clean("(MULTI) FHD"))
        assertEquals("", DisplayTitle.clean("   "))
    }

    @Test
    fun bracketGroupsWithOrdinaryWordsAreKept() {
        assertEquals("Le Grand Voyage (Version longue)", DisplayTitle.clean("Le Grand Voyage (Version longue)"))
    }

    @Test
    fun providerTitlesEndingWithAYearKeepTheYear() {
        // Format observed on the Pixel 7 recette (bloc #211).
        assertEquals("Animals (2026)", DisplayTitle.clean("Animals (MULTI) FHD 2026"))
        assertEquals("Matchbox \u2013 Le film (2026)", DisplayTitle.clean("Matchbox \u2013 Le film (MULTI) FHD 2026"))
        assertEquals("Le Grand Voyage (2021)", DisplayTitle.clean("Le Grand Voyage [4K] (2021)"))
        assertEquals("Le Grand Voyage (2021)", DisplayTitle.clean("FR | Le Grand Voyage 2021"))
    }

    @Test
    fun yearsThatBelongToTheTitleAreKept() {
        assertEquals("Blade Runner 2049", DisplayTitle.clean("Blade Runner 2049"))
        assertEquals("Animals 2026", DisplayTitle.clean("Animals 2026"))
        assertEquals("Le Grand Voyage (2021)", DisplayTitle.clean("Le Grand Voyage (2021)"))
        assertEquals("1917", DisplayTitle.clean("1917"))
        assertEquals("(MULTI) FHD 2026", DisplayTitle.clean("(MULTI) FHD 2026"))
    }
}
