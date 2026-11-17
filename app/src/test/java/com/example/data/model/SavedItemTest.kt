package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedItemTest {

  @Test
  fun normalize_collapsesWhitespaceAndLowercases() {
    assertEquals("steel thali 12 inch", SavedItem.normalize("  Steel   Thali  12 Inch "))
  }

  @Test
  fun buildDocumentId_isDeterministicAndMatchesFirestoreRulesRegex() {
    val id1 = SavedItem.buildDocumentId("Steel Thali")
    val id2 = SavedItem.buildDocumentId("  steel   thali ")
    assertEquals(id1, id2)
    assertTrue(id1.matches(Regex("^[a-zA-Z0-9_\\-]+$")))
    assertTrue(id1.length <= 128)
  }

  @Test
  fun buildDocumentId_handlesNonAsciiNamesSafely() {
    val id = SavedItem.buildDocumentId("स्टील थाली 10\"")
    assertTrue(id.matches(Regex("^[a-zA-Z0-9_\\-]+$")))
    assertTrue(id.isNotEmpty() && id.length <= 128)
  }
}
