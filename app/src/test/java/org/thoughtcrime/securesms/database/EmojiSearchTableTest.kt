/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.network.util.JsonUtil
import org.thoughtcrime.securesms.database.model.EmojiSearchData
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SignalDatabaseRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class EmojiSearchTableTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val signalDatabaseRule = SignalDatabaseRule()

  @Test
  fun `the plain query ranks an exact label first, then a label that starts with the query, then one that contains it later`() {
    setSearchIndex(emoji("🙌", "raised hand"), emoji("🤝", "handshake"), emoji("✋", "hand"))

    val result = SignalDatabase.emojiSearch.query("hand", 10)

    assertThat(result).containsExactly("✋", "🤝", "🙌")
  }

  @Test
  fun `the plain query ranks the more popular emoji first for the same label`() {
    setSearchIndex(emoji("✋", "hand", rank = 2), emoji("🤚", "hand", rank = 1))

    val result = SignalDatabase.emojiSearch.query("hand", 10)

    assertThat(result).containsExactly("🤚", "✋")
  }

  @Test
  fun `the plain query returns an emoji with several matching labels once`() {
    setSearchIndex(emoji("✋", "hand", "handshake"))

    val result = SignalDatabase.emojiSearch.query("hand", 10)

    assertThat(result).containsExactly("✋")
  }

  @Test
  fun `the plain query does not find a label by fragments`() {
    setSearchIndex(emoji("✋", "raised hand"))

    val result = SignalDatabase.emojiSearch.query("raha", 10)

    assertThat(result).isEmpty()
  }

  @Test
  fun `a label is found by fragments that each begin one of its words`() {
    setSearchIndex(emoji("✋", "raised hand"), emoji("🎩", "hat"))

    val result = SignalDatabase.emojiSearch.queryWithWordFragments("raha", 10)

    assertThat(result).containsExactly("✋")
  }

  @Test
  fun `a label is found by fragments separated with a space`() {
    setSearchIndex(emoji("✋", "raised hand"))

    val result = SignalDatabase.emojiSearch.queryWithWordFragments("ra ha", 10)

    assertThat(result).containsExactly("✋")
  }

  @Test
  fun `a label is not found by letters that are in order but do not begin its words`() {
    setSearchIndex(emoji("🉐", "ideograph advantage"))

    val result = SignalDatabase.emojiSearch.queryWithWordFragments("raha", 10)

    assertThat(result).isEmpty()
  }

  @Test
  fun `a fragment match ranks above a substring match in the middle of a word`() {
    setSearchIndex(emoji("🎩", "abraham"), emoji("✋", "raised hand"))

    val result = SignalDatabase.emojiSearch.queryWithWordFragments("raha", 10)

    assertThat(result).containsExactly("✋", "🎩")
  }

  @Test
  fun `a substring match at the start of its label ranks above a fragment match`() {
    setSearchIndex(emoji("🙏", "high five"), emoji("✋", "hifive"))

    val result = SignalDatabase.emojiSearch.queryWithWordFragments("hifi", 10)

    assertThat(result).containsExactly("✋", "🙏")
  }

  @Test
  fun `fragment matches are left out when substring matches fill the limit`() {
    setSearchIndex(emoji("🎩", "abraham"), emoji("✋", "raised hand"))

    val result = SignalDatabase.emojiSearch.queryWithWordFragments("raha", 1)

    assertThat(result).containsExactly("🎩")
  }

  @Test
  fun `fragment matches are cut to the limit`() {
    setSearchIndex(emoji("🤚", "raised back of hand"), emoji("🙌", "raised hands"), emoji("✋", "raised hand"))

    val result = SignalDatabase.emojiSearch.queryWithWordFragments("raha", 2)

    assertThat(result).containsExactly("✋", "🙌")
  }

  @Test
  fun `an emoji found by a substring and by fragments is returned once`() {
    setSearchIndex(emoji("✋", "raised hand", "abraham"))

    val result = SignalDatabase.emojiSearch.queryWithWordFragments("raha", 10)

    assertThat(result).containsExactly("✋")
  }

  @Test
  fun `a fragment match with fewer fragments ranks first`() {
    setSearchIndex(emoji("🔤", "a b c"), emoji("🆎", "ab, c"))

    val result = SignalDatabase.emojiSearch.queryWithWordFragments("abc", 10)

    assertThat(result).containsExactly("🆎", "🔤")
  }

  @Test
  fun `a fragment match that starts earlier in its label ranks first`() {
    setSearchIndex(emoji("🙋", "man raising hand"), emoji("🤚", "raised back of hand"))

    val result = SignalDatabase.emojiSearch.queryWithWordFragments("raha", 10)

    assertThat(result).containsExactly("🤚", "🙋")
  }

  @Test
  fun `a fragment match that leaves less of its label unmatched ranks first`() {
    setSearchIndex(emoji("🙋", "raise a hand"), emoji("✋", "raised hand"))

    val result = SignalDatabase.emojiSearch.queryWithWordFragments("raha", 10)

    assertThat(result).containsExactly("✋", "🙋")
  }

  @Test
  fun `the more popular emoji ranks first for the same fragment match`() {
    setSearchIndex(emoji("✋", "raised hand", rank = 2), emoji("🙌", "raised hand", rank = 1))

    val result = SignalDatabase.emojiSearch.queryWithWordFragments("raha", 10)

    assertThat(result).containsExactly("🙌", "✋")
  }

  @Test
  fun `an emoji as the query still finds only itself`() {
    setSearchIndex(emoji("🙌", "raised hands"), emoji("🎩", "hat"))

    val result = SignalDatabase.emojiSearch.queryWithWordFragments("🙌", 10)

    assertThat(result).containsExactly("🙌")
  }

  private fun setSearchIndex(vararg searchData: EmojiSearchData) {
    SignalDatabase.emojiSearch.setSearchIndex(localizedSearchIndex = searchData.toList(), englishSearchIndex = emptyList())
  }

  private fun emoji(emoji: String, vararg tags: String, rank: Int = 1): EmojiSearchData {
    val json = """{"emoji":"$emoji","rank":$rank,"tags":[${tags.joinToString(",") { tag -> "\"$tag\"" }}]}"""

    return JsonUtil.fromJson(json, EmojiSearchData::class.java)
  }
}
