package org.thoughtcrime.securesms.emoji

import android.net.Uri
import androidx.annotation.WorkerThread
import org.thoughtcrime.securesms.components.emoji.Emoji
import org.thoughtcrime.securesms.components.emoji.EmojiPageModel
import org.thoughtcrime.securesms.components.emoji.StaticEmojiPageModel
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiDrawInfo
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiTree
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.ScreenDensity
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * The entry point for the application to request Emoji data for custom emojis.
 */
class EmojiSource(
  val decodeScale: Float,
  private val emojiData: EmojiData,
  private val emojiPageFactory: EmojiPageFactory
) : EmojiData by emojiData {

  val variationsToCanonical: Map<String, String> by lazy {
    val map = mutableMapOf<String, String>()

    for (page: EmojiPageModel in dataPages) {
      for (emoji: Emoji in page.displayEmoji) {
        for (variation: String in emoji.variations) {
          map[variation] = emoji.value
        }
      }
    }

    map
  }

  val canonicalToVariations: Map<String, List<String>> by lazy {
    val map = mutableMapOf<String, List<String>>()

    for (page: EmojiPageModel in dataPages) {
      for (emoji: Emoji in page.displayEmoji) {
        map[emoji.value] = emoji.variations
      }
    }

    map
  }

  val maxEmojiLength: Int by lazy {
    dataPages.map { it.emoji.map(String::length) }
      .flatten()
      .maxOrZero()
  }

  val emojiTree: EmojiTree by lazy {
    val tree = EmojiTree()

    dataPages
      .filter { it.spriteUri != null }
      .forEach { page ->
        val emojiPage = emojiPageFactory(page.spriteUri!!)

        var overallIndex = 0
        page.displayEmoji.forEach { emoji: Emoji ->
          emoji.variations.forEachIndexed { variationIndex, variation ->
            val raw = emoji.getRawVariation(variationIndex)
            tree.add(variation, EmojiDrawInfo(emojiPage, overallIndex++, variation, raw, jumboPages[raw]))
          }
        }
      }

    obsolete.forEach {
      tree.add(it.obsolete, tree.getEmoji(it.replaceWith, 0, it.replaceWith.length))
    }

    tree
  }

  companion object {

    private val emojiSource = AtomicReference<EmojiSource>()
    private val emojiLatch = CountDownLatch(1)

    @JvmStatic
    val latest: EmojiSource
      get() {
        emojiLatch.await()
        return emojiSource.get()
      }

    @JvmStatic
    @WorkerThread
    fun refresh() {
      emojiSource.set(getEmojiSource())
      emojiLatch.countDown()
    }

    private fun getEmojiSource(): EmojiSource {
      return loadRemoteBasedEmojis() ?: loadAssetBasedEmojis()
    }

    private fun loadRemoteBasedEmojis(): EmojiSource? {
      if (SignalStore.internal.forceBuiltInEmoji()) {
        return null
      }

      val context = AppDependencies.application
      val version = EmojiFiles.Version.readVersion(context) ?: return null
      val emojiData = EmojiFiles.getLatestEmojiData(context, version)?.let {
        it.copy(
          displayPages = it.displayPages + PAGE_EMOTICONS,
          dataPages = it.dataPages + PAGE_EMOTICONS
        )
      }
      val density = ScreenDensity.xhdpiRelativeDensityScaleFactor(version.density)

      return emojiData?.let {
        EmojiSource(density, it) { uri: Uri -> EmojiPage.Disk(uri) }
      }
    }

    private fun loadAssetBasedEmojis(): EmojiSource {
      val emojiData: InputStream = AppDependencies.application.assets.open("emoji/emoji_data.json")

      emojiData.use {
        val parsedData: ParsedEmojiData = EmojiJsonParser.parse(it, ::getAssetsUri).getOrThrow()
        return EmojiSource(
          ScreenDensity.xhdpiRelativeDensityScaleFactor("xhdpi"),
          parsedData.copy(
            displayPages = parsedData.displayPages + PAGE_EMOTICONS,
            dataPages = parsedData.dataPages + PAGE_EMOTICONS
          )
        ) { uri: Uri -> EmojiPage.Asset(uri) }
      }
    }
  }
}

private fun List<Int>.maxOrZero(): Int = maxOrNull() ?: 0

interface EmojiData {
  val metrics: EmojiMetrics
  val densities: List<String>
  val format: String
  val displayPages: List<EmojiPageModel>
  val dataPages: List<EmojiPageModel>
  val jumboPages: Map<String, String>
  val obsolete: List<ObsoleteEmoji>
}

data class ObsoleteEmoji(val obsolete: String, val replaceWith: String)

data class EmojiMetrics(val rawHeight: Int, val rawWidth: Int, val perRow: Int)

private fun getAssetsUri(name: String, format: String): Uri = Uri.parse("file:///android_asset/emoji/$name.$format")

private val PAGE_EMOTICONS: EmojiPageModel = StaticEmojiPageModel(
  EmojiCategory.EMOTICONS,
  arrayOf(
    ":-)", ";-)", "(-:", ":->", ":-D", "\\o/",
    ":-P", "B-)", ":-$", ":-*", "O:-)", "=-O",
    "O_O", "O_o", "o_O", ":O", ":-!", ":-x",
    ":-|", ":-\\", ":-(", ":'(", ":-[", ">:-(",
    "^.^", "^_^", "\\(\u02c6\u02da\u02c6)/",
    "\u30fd(\u00b0\u25c7\u00b0 )\u30ce", "\u00af\\(\u00b0_o)/\u00af",
    "\u00af\\_(\u30c4)_/\u00af", "(\u00ac_\u00ac)",
    "(>_<)", "(\u2565\ufe4f\u2565)", "(\u261e\uff9f\u30ee\uff9f)\u261e",
    "\u261c(\uff9f\u30ee\uff9f\u261c)", "\u261c(\u2312\u25bd\u2312)\u261e",
    "(\u256f\u00b0\u25a1\u00b0)\u256f\ufe35", "\u253b\u2501\u253b",
    "\u252c\u2500\u252c", "\u30ce(\u00b0\u2013\u00b0\u30ce)",
    "(^._.^)\uff89", "\u0e05^\u2022\ufecc\u2022^\u0e05",
    "\u0295\u2022\u1d25\u2022\u0294", "(\u2022_\u2022)",
    " \u25a0-\u25a0\u00ac <(\u2022_\u2022) ", "(\u25a0_\u25a0\u00ac)",
    "\u01aa(\u0693\u05f2)\u200e\u01aa\u200b\u200b"
  ),
  null
)
