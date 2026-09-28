package org.thoughtcrime.securesms.audio

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import org.junit.Test
import java.util.concurrent.TimeUnit

class AudioFileInfoTest {

  @Test
  fun `empty wave form produces an empty result`() {
    val info = AudioFileInfo(0, ByteArray(0))

    assertThat(info.waveForm.size).isEqualTo(0)
    assertThat(info.barCount).isEqualTo(0)
  }

  @Test
  fun `wave form is always resampled to the render bar count`() {
    for (sourceSize in listOf(1, 5, AudioWaveFormGenerator.BAR_COUNT, 100)) {
      val info = AudioFileInfo(0, ByteArray(sourceSize) { 255.toByte() })

      assertThat(info.waveForm.size).isEqualTo(AudioWaveFormGenerator.BAR_COUNT)
      assertThat(info.barCount).isEqualTo(sourceSize)
      assertThat(info.waveForm.toList()).each { it.isCloseTo(1f, 0.001f) }
    }
  }

  @Test
  fun `bytes are read as unsigned and normalized`() {
    val info = AudioFileInfo(0, ByteArray(AudioWaveFormGenerator.BAR_COUNT) { 128.toByte() })

    assertThat(info.waveForm.toList()).each { it.isCloseTo(128f / 255f, 0.001f) }
  }

  @Test
  fun `downsampling averages the source bars`() {
    val source = ByteArray(AudioWaveFormGenerator.BAR_COUNT * 2) { if (it % 2 == 0) 0 else 255.toByte() }

    val info = AudioFileInfo(0, source)

    assertThat(info.waveForm.toList()).each { it.isCloseTo(0.5f, 0.001f) }
  }

  @Test
  fun `duration is converted from microseconds`() {
    val info = AudioFileInfo(TimeUnit.SECONDS.toMicros(3), byteArrayOf(1))

    assertThat(info.getDuration(TimeUnit.MILLISECONDS)).isEqualTo(3000L)
  }

  @Test
  fun `database protobuf round trip`() {
    val original = AudioFileInfo(1234, byteArrayOf(1, 2, 3), isSenderProvided = true)

    val restored = AudioFileInfo.fromDatabaseProtobuf(original.toDatabaseProtobuf())

    assertThat(restored.getDuration(TimeUnit.MICROSECONDS)).isEqualTo(1234L)
    assertThat(restored.barCount).isEqualTo(3)
    assertThat(restored.isSenderProvided).isEqualTo(true)
  }
}
