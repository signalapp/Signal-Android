package org.thoughtcrime.securesms.emoji

import android.net.Uri
import androidx.annotation.WorkerThread
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.Emoji
import org.thoughtcrime.securesms.components.emoji.EmojiPageModel
import org.thoughtcrime.securesms.components.emoji.StaticEmojiPageModel
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiDrawInfo
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiTree
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader
import org.thoughtcrime.securesms.util.ScreenDensity
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * The entry point for the application to request Emoji data for custom emojis.
 */
class EmojiSource(
  val decodeScale: Float,
  private val emojiData: EmojiData,
  private val emojiPageReferenceFactory: EmojiPageReferenceFactory
) : EmojiData by emojiData {

  val variationMap: Map<String, String> by lazy {
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
        val reference = emojiPageReferenceFactory(page.spriteUri!!)
        page.emoji.forEachIndexed { idx, emoji ->
          tree.add(emoji, EmojiDrawInfo(reference, idx))
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
      val context = ApplicationDependencies.getApplication()
      val version = EmojiFiles.Version.readVersion(context) ?: return null
      val emojiData = EmojiFiles.getLatestEmojiData(context, version)
      val density = ScreenDensity.xhdpiRelativeDensityScaleFactor(version.density)

      return emojiData?.let {
        val decodeScale = min(1f, context.resources.getDimension(R.dimen.emoji_drawer_size) / it.metrics.rawHeight)

        EmojiSource(decodeScale * density, it) { uri: Uri -> EmojiPageReference(DecryptableStreamUriLoader.DecryptableUri(uri)) }
      }
    }

    private fun loadAssetBasedEmojis(): EmojiSource {
      val context = ApplicationDependencies.getApplication()
      val emojiData: InputStream = ApplicationDependencies.getApplication().assets.open("emoji/emoji_data.json")

      emojiData.use {
        val parsedData: ParsedEmojiData = EmojiJsonParser.parse(it, ::getAssetsUri).getOrThrow()
        val decodeScale = min(1f, context.resources.getDimension(R.dimen.emoji_drawer_size) / parsedData.metrics.rawHeight)
        return EmojiSource(
          decodeScale * ScreenDensity.xhdpiRelativeDensityScaleFactor("xhdpi"),
          parsedData.copy(
            displayPages = parsedData.displayPages + PAGE_EMOTICONS,
            dataPages = parsedData.dataPages + PAGE_EMOTICONS
          )
        ) { uri: Uri -> EmojiPageReference(uri) }
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
  val obsolete: List<ObsoleteEmoji>
}

data class ObsoleteEmoji(val obsolete: String, val replaceWith: String)

data class EmojiMetrics(val rawHeight: Int, val rawWidth: Int, val perRow: Int)

private fun getAssetsUri(name: String, format: String): Uri = Uri.parse("file:///android_asset/emoji/$name.$format")

private val PAGE_EMOTICONS: EmojiPageModel = StaticEmojiPageModel(
  EmojiCategory.EMOTICONS.icon,
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
