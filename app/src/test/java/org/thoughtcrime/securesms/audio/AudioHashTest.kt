package org.thoughtcrime.securesms.audio

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.signalservice.api.messages.SignalServiceMessageLimits

class AudioHashTest {

  @Test
  fun `fromSenderProvided - null wave form`() {
    assertThat(AudioHash.fromSenderProvided(MediaUtil.AUDIO_AAC, null, 1f)).isNull()
  }

  @Test
  fun `fromSenderProvided - empty wave form`() {
    assertThat(AudioHash.fromSenderProvided(MediaUtil.AUDIO_AAC, ByteArray(0), 1f)).isNull()
  }

  @Test
  fun `fromSenderProvided - wave form exceeding max bar count`() {
    val tooLong = ByteArray(SignalServiceMessageLimits.MAX_AUDIO_WAVEFORM_BAR_COUNT + 1)
    assertThat(AudioHash.fromSenderProvided(MediaUtil.AUDIO_AAC, tooLong, 1f)).isNull()
  }

  @Test
  fun `fromSenderProvided - max bar count is allowed`() {
    val maxLength = ByteArray(SignalServiceMessageLimits.MAX_AUDIO_WAVEFORM_BAR_COUNT)
    assertThat(AudioHash.fromSenderProvided(MediaUtil.AUDIO_AAC, maxLength, 1f)).isNotNull()
  }

  @Test
  fun `fromSenderProvided - non-audio content types are rejected`() {
    assertThat(AudioHash.fromSenderProvided(null, byteArrayOf(1), 1f)).isNull()
    assertThat(AudioHash.fromSenderProvided(MediaUtil.IMAGE_JPEG, byteArrayOf(1), 1f)).isNull()
    assertThat(AudioHash.fromSenderProvided(MediaUtil.VIDEO_MP4, byteArrayOf(1), 1f)).isNull()
    assertThat(AudioHash.fromSenderProvided(MediaUtil.AUDIO_UNSPECIFIED, byteArrayOf(1), 1f)).isNotNull()
  }

  @Test
  fun `fromSenderProvided - retains wave form, duration, and sender provided flag`() {
    val waveForm = byteArrayOf(0, 64, -1)

    val hash = AudioHash.fromSenderProvided(MediaUtil.AUDIO_AAC, waveForm, 2.5f)!!

    assertThat(hash.waveFormBytes.toList()).isEqualTo(waveForm.toList())
    assertThat(hash.durationSeconds!!).isCloseTo(2.5f, 0.001f)
    assertThat(hash.audioWaveForm.senderProvided).isTrue()
  }

  @Test
  fun `fromSenderProvided - non-positive durations are dropped`() {
    assertThat(AudioHash.fromSenderProvided(MediaUtil.AUDIO_AAC, byteArrayOf(1), null)!!.durationSeconds).isNull()
    assertThat(AudioHash.fromSenderProvided(MediaUtil.AUDIO_AAC, byteArrayOf(1), 0f)!!.durationSeconds).isNull()
    assertThat(AudioHash.fromSenderProvided(MediaUtil.AUDIO_AAC, byteArrayOf(1), -1f)!!.durationSeconds).isNull()
    assertThat(AudioHash.fromSenderProvided(MediaUtil.AUDIO_AAC, byteArrayOf(1), Float.NaN)!!.durationSeconds).isNull()
    assertThat(AudioHash.fromSenderProvided(MediaUtil.AUDIO_AAC, byteArrayOf(1), Float.NEGATIVE_INFINITY)!!.durationSeconds).isNull()
  }

  @Test
  fun `fromSenderProvided - oversized durations are clamped`() {
    val max = SignalServiceMessageLimits.MAX_AUDIO_DURATION_SECONDS

    listOf(max + 1f, 1e30f, Float.MAX_VALUE, Float.POSITIVE_INFINITY).forEach { duration ->
      val hash = AudioHash.fromSenderProvided(MediaUtil.AUDIO_AAC, byteArrayOf(1), duration)!!
      assertThat(hash.durationSeconds!!).isCloseTo(max, 0.001f)
    }
  }

  @Test
  fun `parseOrNull - round trips a sender provided hash`() {
    val original = AudioHash.fromSenderProvided(MediaUtil.AUDIO_AAC, byteArrayOf(1, 2, 3), 4f)!!

    val parsed = AudioHash.parseOrNull(original.hash)!!

    assertThat(parsed).isEqualTo(original)
    assertThat(parsed.waveFormBytes.toList()).isEqualTo(listOf<Byte>(1, 2, 3))
  }

  @Test
  fun `parseOrNull - null and garbage input`() {
    assertThat(AudioHash.parseOrNull(null)).isNull()
    assertThat(AudioHash.parseOrNull("not base64!!")).isNull()
  }
}
