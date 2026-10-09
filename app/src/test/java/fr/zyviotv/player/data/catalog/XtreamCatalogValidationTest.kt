package fr.zyviotv.player.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamCatalogValidationTest {
    @Test
    fun bodyKindIsReadFromItsFirstCharacters() {
        assertEquals(XtreamBodyKind.JsonArray, XtreamBodyKind.of(" [{\"stream_id\":1}]"))
        assertEquals(XtreamBodyKind.EmptyArray, XtreamBodyKind.of("[ ]"))
        assertEquals(XtreamBodyKind.EmptyArray, XtreamBodyKind.of("﻿[]"))
        assertEquals(XtreamBodyKind.JsonObject, XtreamBodyKind.of("{\"user_info\":{\"auth\":0}}"))
        assertEquals(XtreamBodyKind.Html, XtreamBodyKind.of("<html>Prohibited</html>"))
        assertEquals(XtreamBodyKind.Empty, XtreamBodyKind.of("   "))
        assertEquals(XtreamBodyKind.Empty, XtreamBodyKind.of("null"))
        assertEquals(XtreamBodyKind.NotJson, XtreamBodyKind.of("Prohibited"))
    }

    @Test
    fun reallyEmptyListIsAcceptedWithoutPreviousCatalogue() {
        listOf(null, 0).forEach { previous ->
            val outcome = XtreamCatalogValidation.check(XtreamBodyKind.EmptyArray, 0, 0, previous)
            assertEquals(XtreamListOutcome.Empty, outcome)
            assertTrue(outcome.accepted)
        }
    }

    @Test
    fun emptyListIsSuspiciousWhenThePreviousCatalogueHadItems() {
        listOf(XtreamBodyKind.EmptyArray, XtreamBodyKind.Empty).forEach { kind ->
            val outcome = XtreamCatalogValidation.check(kind, 0, 0, previousCount = 12_607)
            assertEquals(XtreamListOutcome.SuspiciousEmpty, outcome)
            assertFalse(outcome.accepted)
        }
    }

    @Test
    fun htmlObjectOrTextIsRejectedEvenWithoutPreviousCatalogue() {
        listOf(XtreamBodyKind.Html, XtreamBodyKind.JsonObject, XtreamBodyKind.NotJson).forEach { kind ->
            val outcome = XtreamCatalogValidation.check(kind, 0, 0, previousCount = null)
            assertEquals(XtreamListOutcome.InvalidBody, outcome)
            assertFalse(outcome.accepted)
        }
    }

    @Test
    fun arrayWithoutAnyUsableEntryIsRejected() {
        assertEquals(
            XtreamListOutcome.NoUsableEntry,
            XtreamCatalogValidation.check(XtreamBodyKind.JsonArray, rawCount = 50, usableCount = 0, previousCount = null),
        )
        assertEquals(
            XtreamListOutcome.Valid,
            XtreamCatalogValidation.check(XtreamBodyKind.JsonArray, rawCount = 50, usableCount = 48, previousCount = 6_074),
        )
    }

    @Test
    fun smallerButNonEmptyListIsNotAnArbitraryRejection() {
        assertEquals(
            XtreamListOutcome.Valid,
            XtreamCatalogValidation.check(XtreamBodyKind.JsonArray, rawCount = 10, usableCount = 10, previousCount = 6_074),
        )
    }

    @Test
    fun rejectionMessagesNameTheListButNoProviderData() {
        XtreamCatalogList.entries.forEach { list ->
            listOf(XtreamListOutcome.SuspiciousEmpty, XtreamListOutcome.InvalidBody, XtreamListOutcome.NoUsableEntry)
                .forEach { outcome ->
                    val message = XtreamCatalogValidation.message(list, outcome)
                    assertTrue(message.contains(list.label))
                    assertTrue(message.contains("conservé"))
                    assertFalse(message.contains("://"))
                }
        }
    }
}
