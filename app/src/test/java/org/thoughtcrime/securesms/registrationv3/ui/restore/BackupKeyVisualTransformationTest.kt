/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.restore

import androidx.compose.ui.text.AnnotatedString
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class BackupKeyVisualTransformationTest {

  companion object {
    private const val TEST_KEY = "AAAABBBBCCCCDDDDEEEEFFFFGGGGHHHHIIIIJJJJKKKKLLLLMMMMNNNNOOOOPPPP"
    private const val FORMATTED_TEST_KEY = "AAAA BBBB CCCC DDDD EEEE FFFF GGGG HHHH IIII JJJJ KKKK LLLL MMMM NNNN OOOO PPPP"
  }

  @Test
  fun `Given a blank entry, ensure offset mapping is 0`() {
    val testSubject = BackupKeyVisualTransformation(chunkSize = 4)

    val result = testSubject.filter(AnnotatedString(""))

    assertThat(result.offsetMapping.originalToTransformed(0)).isEqualTo(0)
  }

  @Test
  fun `Given a single entry, ensure offset mapping is 0`() {
    val testSubject = BackupKeyVisualTransformation(chunkSize = 4)

    val result = testSubject.filter(AnnotatedString("A"))

    assertThat(result.offsetMapping.originalToTransformed(1)).isEqualTo(1)
  }

  @Test
  fun `Given a backup key, ensure a space comes after every 4 characters`() {
    val testSubject = BackupKeyVisualTransformation(chunkSize = 4)
    val text = AnnotatedString(TEST_KEY)
    val expected = AnnotatedString(FORMATTED_TEST_KEY)

    val transformed = testSubject.filter(text)
    val result = transformed.text

    assertThat(result).isEqualTo(expected)
  }

  @Test
  fun `Given output length, when I originalToTransformed, then I expect proper output`() {
    val testSubject = BackupKeyVisualTransformation(chunkSize = 4)
    val text = AnnotatedString("AAAAAAAA")

    val transformed = testSubject.filter(text)
    val result = transformed.offsetMapping

    assertThat(result.originalToTransformed(text.length)).isEqualTo(9)
  }

  @Test
  fun `When I originalToTransformed, then I expect proper output`() {
    val testSubject = BackupKeyVisualTransformation(chunkSize = 4)
    val text = AnnotatedString("AAAAA")
    val expected = listOf(0, 1, 2, 3, 5, 6)

    val transformed = testSubject.filter(text)
    val result = transformed.offsetMapping
    val mapping = (0..text.length).map { result.originalToTransformed(it) }
    assertThat(mapping).isEqualTo(expected)
  }

  @Test
  fun `Given output length, when I transformedToOriginal, then I expect proper output`() {
    val testSubject = BackupKeyVisualTransformation(chunkSize = 4)
    val text = AnnotatedString("AAAAAAAA")

    val transformed = testSubject.filter(text)
    val result = transformed

    assertThat(result.offsetMapping.transformedToOriginal(result.text.length)).isEqualTo(text.length)
  }

  @Test
  fun `When I transformedToOriginal, then I expect proper output`() {
    val testSubject = BackupKeyVisualTransformation(chunkSize = 4)
    val original = AnnotatedString(TEST_KEY)
    val expected = (0 until original.length).toSet()

    val transformed = testSubject.filter(original)
    val result = transformed.offsetMapping
    val mapping = transformed.text.indices.map { result.transformedToOriginal(it) }
    assertThat(mapping.toSet()).isEqualTo(expected)
  }
}
