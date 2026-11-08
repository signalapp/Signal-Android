/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.buildtools

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.Text
import java.io.File
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.ceil
import kotlin.math.min

/**
 * A stand-in for a family of translations, generated from the English string resources so that layouts can be
 * stress tested against it before real translations land. Each is written to a locale from ISO 639's private-use
 * range, [locale], which no real translation will ever use.
 *
 * Each string grows to the length our translations reach for an English string of its length, about the 99th
 * percentile across every locale: short strings grow the most. Words grow by [wordGrowth], about how much longer the
 * family's typical word is than English's, and words copied from the string make up the rest. Letters are accented so
 * that tall glyphs are measured too. No word grows past [MAX_WORD_LENGTH], about the longest word in a German, Finnish
 * or Dutch string at the 95th percentile.
 */
enum class PseudoLanguage(val locale: String, private val wordGrowth: Float) {
  /** More words, a little longer, with the same places to break between them, like French or Dutch. */
  EXPANSIVE("qaa", wordGrowth = 1.25f),

  /** Longer words joined into compounds with nowhere to break, like German or Finnish. */
  COMPOUND("qab", wordGrowth = 1.5f);

  /** The resource directory the language is written to. */
  val valuesDirectoryName: String = "values-b+$locale"

  /**
   * Leaves format specifiers, escapes, markup and URLs alone.
   */
  fun translate(text: String): String {
    return translate(text, protectedMask(text)).text
  }

  /**
   * Translates a `strings.xml` file, leaving out the strings that aren't translatable. Each string's markup is kept,
   * as is anything inside an `xliff:g`.
   */
  fun translateStringsXml(englishXml: File): String {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(englishXml)
    val resources = document.documentElement

    for (node in resources.childNodes.toList()) {
      if (node is Text && node.data.isBlank()) {
        continue
      }

      val keep = node is Element && node.tagName in STRING_RESOURCES && node.getAttribute("translatable") != "false"
      if (!keep) {
        resources.removeChild(node)
        continue
      }

      node as Element
      if (node.tagName == "string") {
        translateElement(node)
      } else {
        node.childNodes.toList().filterIsInstance<Element>().filter { it.tagName == "item" }.forEach(::translateElement)
      }
    }

    val writer = StringWriter()
    TransformerFactory.newInstance().newTransformer().apply {
      setOutputProperty(OutputKeys.ENCODING, "UTF-8")
    }.transform(DOMSource(document), StreamResult(writer))
    return writer.toString()
  }

  /**
   * Translates the text across all of [element]'s text nodes as one string, so that it grows by the right amount,
   * then hands each node back its share.
   */
  private fun translateElement(element: Element) {
    val segments = mutableListOf<Pair<Text, Boolean>>()
    collectText(element, insideXliff = false, segments)

    val text = segments.joinToString("") { it.first.data }
    if (text.trimStart().startsWith("@") || text.trimStart().startsWith("?")) {
      return
    }

    val mask = protectedMask(text)
    var offset = 0
    for ((node, insideXliff) in segments) {
      if (insideXliff) {
        mask.fill(true, offset, offset + node.data.length)
      }
      offset += node.data.length
    }

    val translated = translate(text, mask)
    offset = 0
    for ((node, _) in segments) {
      val end = offset + node.data.length
      node.data = translated.text.substring(translated.offsets[offset], translated.offsets[end])
      offset = end
    }
  }

  private fun collectText(node: Node, insideXliff: Boolean, segments: MutableList<Pair<Text, Boolean>>) {
    for (child in node.childNodes.toList()) {
      when (child) {
        is Text -> segments += child to insideXliff
        is Element -> collectText(child, insideXliff || child.tagName == "xliff:g", segments)
        else -> node.removeChild(child)
      }
    }
  }

  private fun translate(text: String, protected: BooleanArray): Translation {
    val words = mutableListOf<IntRange>()
    var i = 0
    while (i < text.length) {
      if (protected[i] || !text[i].isLetter()) {
        i++
        continue
      }
      val start = i
      while (i < text.length && !protected[i] && text[i].isLetter()) {
        i++
      }
      words += start until i
    }

    if (words.isEmpty()) {
      return Translation(text, IntArray(text.length + 1) { it })
    }

    val stretched = words.map { word -> stretch(text.substring(word.first, word.last + 1), ceil(word.count() * wordGrowth).toInt()) }

    val unprotectedLength = protected.count { !it }
    val targetLength = ceil(unprotectedLength * expansion(unprotectedLength)).toInt()
    var missing = targetLength - unprotectedLength - stretched.sumOf { it.length } + words.sumOf { it.count() }
    val padding = mutableListOf<String>()
    while (missing > 1) {
      val word = stretched[padding.size % stretched.size].lowercase().take(missing - 1)
      padding += word
      missing -= word.length + 1
    }

    // Each word, keyed by where it starts, replaced by its translation and with any padding after the last
    val replacements = mutableMapOf<Int, Pair<Int, String>>()
    words.forEachIndexed { index, word -> replacements[word.first] = word.last + 1 to stretched[index] }

    // Words with no whitespace between them, like "device's" or "auto-download", can't be broken apart, so they are
    // one unit and only whole units are joined
    val units = mutableListOf(mutableListOf(0))
    for (index in 1 until words.size) {
      val gap = text.substring(words[index - 1].last + 1, words[index].first)
      if (gap.none { it.isWhitespace() }) {
        units.last() += index
      } else {
        units += mutableListOf(index)
      }
    }
    val unitLengths = units.map { unit -> words[unit.last()].last + 1 - words[unit.first()].first + unit.sumOf { stretched[it].length - words[it].count() } }

    var runLength = unitLengths.first()
    for (index in 1 until units.size) {
      val previous = words[units[index - 1].last()]
      val next = words[units[index].first()]
      if (joins(runLength, unitLengths[index]) && next.first == previous.last + 2 && text[previous.last + 1] == ' ') {
        replacements[previous.last + 1] = previous.last + 2 to ""
        runLength += unitLengths[index]
      } else {
        runLength = unitLengths[index]
      }
    }

    val last = words.last()
    replacements[last.first] = last.last + 1 to buildString {
      append(stretched.last())
      for (word in padding) {
        if (joins(runLength, word.length)) {
          runLength += word.length
        } else {
          append(' ')
          runLength = word.length
        }
        append(word)
      }
    }

    val offsets = IntArray(text.length + 1)
    val translated = StringBuilder()
    var position = 0
    while (position < text.length) {
      offsets[position] = translated.length
      val replacement = replacements[position]
      if (replacement != null) {
        val (end, value) = replacement
        translated.append(value)
        (position + 1 until end).forEach { offsets[it] = translated.length }
        position = end
      } else {
        translated.append(text[position])
        position++
      }
    }
    offsets[text.length] = translated.length

    return Translation(translated.toString(), offsets)
  }

  private fun joins(runLength: Int, nextLength: Int): Boolean {
    return this == COMPOUND && runLength + nextLength <= MAX_WORD_LENGTH
  }

  /** [offsets] maps each position in the original text to where it ended up in [text]. */
  private class Translation(val text: String, val offsets: IntArray)

  companion object {
    const val MAX_WORD_LENGTH = 20

    private val STRING_RESOURCES = setOf("string", "plurals", "string-array")

    private val PROTECTED = listOf(
      // Format specifiers, e.g. %1$s, %d, %.1f, %%
      Regex("""%(\d+\$)?[-#+ 0,(<]*\d*(\.\d+)?[a-zA-Z%]"""),
      // Resource escapes, e.g. \n, \', …
      Regex("""\\u[0-9a-fA-F]{4}|\\."""),
      // Markup inside CDATA, e.g. <b> or <a href="...">, and entities
      Regex("""<[^>]*>|&#?\w+;"""),
      Regex("""[a-zA-Z][a-zA-Z0-9+.-]*://[^\s"'<>]+""")
    )

    private const val ACCENTED_UPPER = "ÅƁÇĐÉƑĜĤÎĴĶĹṀÑÖÞǪŔŠŢÛṼŴẊÝŽ"
    private const val ACCENTED_LOWER = "åƀçđéƒĝĥîĵķĺɱñöþǫŕšţûṽŵẋýž"

    private fun protectedMask(text: String): BooleanArray {
      val mask = BooleanArray(text.length)
      PROTECTED.forEach { regex -> regex.findAll(text).forEach { match -> mask.fill(true, match.range.first, match.range.last + 1) } }
      return mask
    }

    /** How much longer translations of an English string of [length] characters get, at about the 99th percentile. */
    private fun expansion(length: Int): Float {
      return when {
        length <= 10 -> 3.25f
        length <= 20 -> 2.45f
        length <= 40 -> 2f
        length <= 80 -> 1.7f
        else -> 1.56f
      }
    }

    private fun stretch(word: String, length: Int): String {
      val accented = word.map(::accent)
      return buildString {
        accented.forEach(::append)
        while (this.length < min(length, MAX_WORD_LENGTH)) {
          append(accented[(this.length - accented.size) % accented.size].lowercaseChar())
        }
      }
    }

    private fun accent(char: Char): Char {
      return when (char) {
        in 'A'..'Z' -> ACCENTED_UPPER[char - 'A']
        in 'a'..'z' -> ACCENTED_LOWER[char - 'a']
        else -> char
      }
    }

    private fun org.w3c.dom.NodeList.toList(): List<Node> = List(length) { item(it) }
  }
}
