/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.search

import android.app.Application
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.endsWith
import assertk.assertions.hasLength
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.startsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.SearchTable

/**
 * Unit tests for the app-side snippet generation in [SearchRepository], which replaced the SQL
 * `snippet()` function. Locks the boundary math, case-insensitivity, and the truncation behavior
 * of the no-match fallback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class SearchRepositoryTest {

  @Test
  fun tokenizeQuerySplitsOnWhitespaceAndDropsEmpties() {
    assertThat(SearchRepository.tokenizeQuery("  hello   world  ")).containsExactly("hello", "world")
  }

  @Test
  fun tokenizeQueryEmptyForBlank() {
    assertThat(SearchRepository.tokenizeQuery("   ")).isEmpty()
  }

  @Test
  fun makeSnippetReturnsShortBodyUnchanged() {
    val body = "short message"
    assertThat(SearchRepository.makeSnippet(listOf("short"), body).toString()).isEqualTo(body)
  }

  @Test
  fun makeSnippetWrapsBothSidesForMatchInMiddle() {
    val body = "0123456789 0123456789 0123456789 needle 1234567890 1234567890 1234567890 1234567890 1234567890"

    val snippet = SearchRepository.makeSnippet(listOf("needle"), body).toString()

    assertThat(snippet).startsWith(SearchTable.SNIPPET_WRAP)
    assertThat(snippet).endsWith(SearchTable.SNIPPET_WRAP)
    assertThat(snippet).contains("needle")
    assertThat(snippet.length < body.length).isTrue()
  }

  @Test
  fun makeSnippetDoesNotWrapStartWhenMatchAtBeginning() {
    val body = "needle 0123456789 0123456789 0123456789 0123456789 0123456789"

    val snippet = SearchRepository.makeSnippet(listOf("needle"), body).toString()

    assertThat(snippet.startsWith(SearchTable.SNIPPET_WRAP)).isFalse()
    assertThat(snippet).contains("needle")
  }

  @Test
  fun makeSnippetIsCaseInsensitive() {
    val body = "0123456789 0123456789 0123456789 needle 1234567890 1234567890 1234567890 1234567890 1234567890"

    val snippet = SearchRepository.makeSnippet(listOf("NEEDLE"), body).toString()

    assertThat(snippet).contains("needle")
  }

  @Test
  fun makeSnippetFallbackReturnsFullBodyUnderMaxSize() {
    val body = "0123456789 0123456789 0123456789 0123456789 0123456789 012"

    val snippet = SearchRepository.makeSnippet(listOf("nomatch"), body).toString()

    assertThat(snippet).isEqualTo(body)
  }

  @Test
  fun makeSnippetFallbackTruncatesLongBodyWithNoMatch() {
    val body = "x".repeat(200)

    val snippet = SearchRepository.makeSnippet(listOf("nomatch"), body).toString()

    assertThat(snippet).hasLength(100 + SearchTable.SNIPPET_WRAP.length)
    assertThat(snippet).endsWith(SearchTable.SNIPPET_WRAP)
  }
}
