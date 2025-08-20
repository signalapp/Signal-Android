
package org.signal.core.util

import android.app.Application
import android.text.SpannedString
import android.widget.TextView
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNull
import okio.utf8Size
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ByteLimitInputFilterTest {

  @Test
  fun `filter - null source, returns null`() {
    val filter = ByteLimitInputFilter(10)
    val result = filter.filter(null, 0, 0, SpannedString(""), 0, 0)
    assertThat(result).isNull()
  }

  @Test
  fun `filter - null dest, returns null`() {
    val filter = ByteLimitInputFilter(10)
    val result = filter.filter("test", 0, 4, null, 0, 0)
    assertThat(result).isNull()
  }

  @Test
  fun `filter - within byte limit, returns null`() {
    val filter = ByteLimitInputFilter(10)
    val existingText = SpannedString("hi")
    val insertText = "test"
    val result = filter.testAppend(insertText, existingText)
    assertThat(result).isNull()
  }

  @Test
  fun `filter - exact byte limit, returns null`() {
    val filter = ByteLimitInputFilter(6)
    val dest = SpannedString("hi")
    val insertText = "test"
    val result = filter.testAppend(insertText, dest)
    assertThat(result).isNull()
  }

  @Test
  fun `filter - exceeds byte limit, returns truncated`() {
    val filter = ByteLimitInputFilter(5)
    val dest = SpannedString("hi")
    val insertText = "test"
    val result = filter.testAppend(insertText, dest)
    assertThat(result.toString()).isEqualTo("tes")
  }

  @Test
  fun `filter - no space available, returns empty`() {
    val filter = ByteLimitInputFilter(2)
    val dest = SpannedString("hi")
    val insertText = "test"
    val result = filter.testAppend(insertText, dest)
    assertThat(result.toString()).isEqualTo("")
  }

  @Test
  fun `filter - insert at beginning`() {
    val filter = ByteLimitInputFilter(6)
    val dest = SpannedString("hi")
    val insertText = "test"
    val result = filter.testPrepend(insertText, dest)
    assertThat(result).isNull()
  }

  @Test
  fun `filter - insert at end`() {
    val filter = ByteLimitInputFilter(6)
    val dest = SpannedString("hi")
    val insertText = "test"
    val result = filter.testAppend(insertText, dest)
    assertThat(result).isNull()
  }

  @Test
  fun `filter - replace text`() {
    val filter = ByteLimitInputFilter(6)
    val dest = SpannedString("hello")
    val insertText = "test"
    val result = filter.testReplaceRange(insertText, dest, 1, 4)
    assertThat(result).isNull()
  }

  @Test
  fun `filter - unicode characters`() {
    val filter = ByteLimitInputFilter(9)
    val dest = SpannedString("hi")
    val insertText = "café"
    val result = filter.testAppend(insertText, dest)
    assertThat(result).isNull()
  }

  @Test
  fun `filter - emoji characters`() {
    val filter = ByteLimitInputFilter(6)
    val dest = SpannedString("hi")
    val insertText = "😀😁"
    assertThat((insertText + dest).utf8Size()).isGreaterThan(6)

    val result = filter.testAppend(insertText, dest)
    assertThat(result.toString()).isEqualTo("😀")
  }

  @Test
  fun `filter - mixed unicode and emoji`() {
    val filter = ByteLimitInputFilter(15)
    val dest = SpannedString("test")
    val insertText = "café😀"
    val result = filter.testAppend(insertText, dest)
    assertThat(result).isNull()
  }

  @Test
  fun `filter - partial source range`() {
    val filter = ByteLimitInputFilter(5)
    val dest = SpannedString("hi")
    val source = "abcdef"
    val result = filter.testPartialSource(source, 1, 4, dest, dest.length)
    assertThat(result).isNull()
  }

  @Test
  fun `filter - long text truncation`() {
    val filter = ByteLimitInputFilter(10)
    val dest = SpannedString("")
    val longText = "this is a very long text that should be truncated"
    val result = filter.testAppend(longText, dest)
    assertThat(result.toString()).isEqualTo("this is a ")
  }

  @Test
  fun `filter - ascii characters`() {
    val filter = ByteLimitInputFilter(5)
    val dest = SpannedString("")
    val insertText = "hello"
    val result = filter.testAppend(insertText, dest)
    assertThat(result).isNull()
  }

  @Test
  fun `filter - surrogate handling`() {
    val filter = ByteLimitInputFilter(8)
    val dest = SpannedString("hi")
    val insertText = "🎉🎊"
    val result = filter.testAppend(insertText, dest)
    assertThat(result.toString()).isEqualTo("🎉")
  }

  @Test
  fun `filter - empty source`() {
    val filter = ByteLimitInputFilter(10)
    val dest = SpannedString("test")
    val insertText = ""
    val result = filter.testInsertAt(insertText, dest, 2)
    assertThat(result).isNull()
  }

  @Test
  fun `filter - empty dest`() {
    val filter = ByteLimitInputFilter(3)
    val dest = SpannedString("")
    val insertText = "test"
    val result = filter.testAppend(insertText, dest)
    assertThat(result.toString()).isEqualTo("tes")
  }

  @Test
  fun `filter - unicode truncation`() {
    val filter = ByteLimitInputFilter(4)
    val dest = SpannedString("")
    val insertText = "café"
    val result = filter.testAppend(insertText, dest)
    assertThat(result.toString()).isEqualTo("caf")
  }

  @Test
  fun `filter - emoji truncation`() {
    val filter = ByteLimitInputFilter(4)
    val dest = SpannedString("")
    val insertText = "😀a"
    val result = filter.testAppend(insertText, dest)
    assertThat(result.toString()).isEqualTo("😀")
  }

  @Test
  fun `filter - insert at middle`() {
    val filter = ByteLimitInputFilter(7)
    val dest = SpannedString("hello")
    val insertText = "XY"
    val result = filter.testInsertAt(insertText, dest, 2)
    assertThat(result).isNull()
  }

  @Test
  fun `filter - insert at middle with truncation`() {
    val filter = ByteLimitInputFilter(6)
    val dest = SpannedString("hello")
    val insertText = "XYZ"
    val result = filter.testInsertAt(insertText, dest, 2)
    assertThat(result.toString()).isEqualTo("X")
  }

  @Test
  fun `textView integration - append within limit`() {
    val textView = TextView(RuntimeEnvironment.getApplication())
    textView.filters = arrayOf(ByteLimitInputFilter(10))

    textView.setText("hi", TextView.BufferType.EDITABLE)
    textView.append("test")

    assertThat(textView.text.toString()).isEqualTo("hitest")
  }

  @Test
  fun `textView integration - append exceeds limit`() {
    val textView = TextView(RuntimeEnvironment.getApplication())
    textView.filters = arrayOf(ByteLimitInputFilter(5))

    textView.setText("hi", TextView.BufferType.EDITABLE)
    textView.append("test")

    assertThat(textView.text.toString()).isEqualTo("hites")
  }

  @Test
  fun `textView integration - replace text with truncation`() {
    val textView = TextView(RuntimeEnvironment.getApplication())
    textView.filters = arrayOf(ByteLimitInputFilter(8))

    textView.setText("hello", TextView.BufferType.EDITABLE)
    val editable = textView.editableText
    editable.replace(3, 5, "test")

    assertThat(textView.text.toString()).isEqualTo("heltest")
  }

  @Test
  fun `textView integration - emoji handling`() {
    val textView = TextView(RuntimeEnvironment.getApplication())
    textView.filters = arrayOf(ByteLimitInputFilter(10))

    textView.setText("hi", TextView.BufferType.EDITABLE)
    textView.append("😀😁")
    assertThat(textView.text.toString().utf8Size()).isEqualTo(10)
  }

  @Test
  fun `textView integration - unicode characters`() {
    val textView = TextView(RuntimeEnvironment.getApplication())
    textView.filters = arrayOf(ByteLimitInputFilter(10))

    textView.setText("hi", TextView.BufferType.EDITABLE)
    textView.append("café")

    assertThat(textView.text.toString()).isEqualTo("hicafé")
  }

  @Test
  fun `textView integration - set text directly`() {
    val textView = TextView(RuntimeEnvironment.getApplication())
    textView.filters = arrayOf(ByteLimitInputFilter(5))

    textView.setText("this is a long text", TextView.BufferType.EDITABLE)

    assertThat(textView.text.toString()).isEqualTo("this ")
  }

  @Test
  fun `textView integration - fuzzing with mixed character types`() {
    val textView = TextView(RuntimeEnvironment.getApplication())
    val byteLimit = 100
    textView.filters = arrayOf(ByteLimitInputFilter(byteLimit))

    val asciiChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|;:,.<>?"
    val unicodeChars = "àáâãäåæçèéêëìíîïñòóôõöøùúûüýÿ"
    val emojiChars = "😀😁😂😃😄😅😆😇😈😉😊😋😌😍😎😏😐😑😒😓😔😕😖😗😘😙😚😛😜😝😞😟😠😡😢😣😤😥😦😧😨😩😪😫😬😭😮😯😰😱😲😳😴😵😶😷😸😹😺😻😼😽😾😿🙀🙁🙂"
    val japaneseChars = "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをんアイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン日本語漢字平仮名片仮名"
    val allChars = asciiChars + unicodeChars + emojiChars + japaneseChars

    repeat(100) { iteration ->
      textView.setText("", TextView.BufferType.EDITABLE)

      val targetLength = 150 + (iteration * 5)
      val randomText = StringBuilder().apply {
        repeat(targetLength) {
          append(allChars.random())
        }
      }

      textView.setText(randomText.toString(), TextView.BufferType.EDITABLE)

      val finalText = textView.text.toString()
      val actualByteSize = finalText.utf8Size()

      assertThat(actualByteSize).isLessThanOrEqualTo((byteLimit).toLong())

      if (randomText.toString().utf8Size() > byteLimit) {
        assertThat(finalText.length).isLessThan(randomText.length)
      }
    }
  }

  private fun ByteLimitInputFilter.testAppend(insertText: String, dest: SpannedString): CharSequence? {
    return this.filter(insertText, 0, insertText.length, dest, dest.length, dest.length)
  }

  private fun ByteLimitInputFilter.testPrepend(insertText: String, dest: SpannedString): CharSequence? {
    return this.filter(insertText, 0, insertText.length, dest, 0, 0)
  }

  private fun ByteLimitInputFilter.testInsertAt(insertText: String, dest: SpannedString, position: Int): CharSequence? {
    return this.filter(insertText, 0, insertText.length, dest, position, position)
  }

  private fun ByteLimitInputFilter.testReplaceRange(insertText: String, dest: SpannedString, startPos: Int, endPos: Int): CharSequence? {
    return this.filter(insertText, 0, insertText.length, dest, startPos, endPos)
  }

  private fun ByteLimitInputFilter.testPartialSource(source: String, startPos: Int, endPos: Int, dest: SpannedString, insertPos: Int): CharSequence? {
    return this.filter(source, startPos, endPos, dest, insertPos, insertPos)
  }
}
