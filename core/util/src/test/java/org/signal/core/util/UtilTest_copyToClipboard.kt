package org.signal.core.util

import android.app.AlarmManager
import android.app.Application
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Verifies that secrets copied to the clipboard are flagged sensitive and scheduled for clearing, and -- just as importantly -- that
 * ordinary text is not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class UtilTest_copyToClipboard {

  private val context: Context
    get() = RuntimeEnvironment.getApplication()

  private val clipboardManager: ClipboardManager
    get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

  private val alarmManager: AlarmManager
    get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

  @Test
  @Config(sdk = [33])
  fun `copyToClipboardSensitive - marks the clip as sensitive`() {
    Util.copyToClipboardSensitive(context, SECRET, 60)

    val extras = clipboardManager.primaryClip!!.description.extras
    assertThat(extras).isNotNull()
    assertThat(extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE)).isTrue()
  }

  @Test
  @Config(sdk = [33])
  fun `copyToClipboardSensitive - still copies the text`() {
    Util.copyToClipboardSensitive(context, SECRET, 60)

    assertThat(clipboardManager.primaryClip!!.getItemAt(0).text.toString()).isEqualTo(SECRET)
  }

  @Test
  @Config(sdk = [33])
  fun `copyToClipboardSensitive - schedules a clear alarm`() {
    val before = System.currentTimeMillis()

    Util.copyToClipboardSensitive(context, SECRET, 60)

    val alarms = shadowOf(alarmManager).scheduledAlarms
    assertThat(alarms.size).isEqualTo(1)
    assertThat(alarms[0].type).isEqualTo(AlarmManager.RTC_WAKEUP)
    assertThat(alarms[0].triggerAtTime).isGreaterThanOrEqualTo(before + TimeUnit.SECONDS.toMillis(60))
  }

  @Test
  @Config(sdk = [26])
  fun `copyToClipboardSensitive - below api 33, does not set extras`() {
    Util.copyToClipboardSensitive(context, SECRET, 60)

    assertThat(clipboardManager.primaryClip!!.description.extras).isNull()
  }

  @Test
  @Config(sdk = [26])
  fun `copyToClipboardSensitive - below api 33, still schedules a clear alarm`() {
    Util.copyToClipboardSensitive(context, SECRET, 60)

    assertThat(shadowOf(alarmManager).scheduledAlarms.size).isEqualTo(1)
  }

  @Test
  @Config(sdk = [33])
  fun `copyToClipboardSensitive - default overload marks the clip sensitive and uses the default timeout`() {
    val before = System.currentTimeMillis()

    Util.copyToClipboardSensitive(context, SECRET)

    val extras = clipboardManager.primaryClip!!.description.extras
    assertThat(extras).isNotNull()
    assertThat(extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE)).isTrue()

    val alarms = shadowOf(alarmManager).scheduledAlarms
    assertThat(alarms.size).isEqualTo(1)
    assertThat(alarms[0].triggerAtTime).isGreaterThanOrEqualTo(before + TimeUnit.SECONDS.toMillis(Util.SENSITIVE_CLIPBOARD_TIMEOUT_SECONDS.toLong()))
  }

  @Test
  @Config(sdk = [33])
  fun `copyToClipboard - does not mark the clip as sensitive`() {
    Util.copyToClipboard(context, NOT_A_SECRET)

    assertThat(clipboardManager.primaryClip!!.description.extras).isNull()
  }

  @Test
  @Config(sdk = [33])
  fun `copyToClipboard - does not schedule a clear alarm`() {
    Util.copyToClipboard(context, NOT_A_SECRET)

    assertThat(shadowOf(alarmManager).scheduledAlarms).isEmpty()
  }

  companion object {
    private const val SECRET = "12345 67890 12345 67890 12345 67890"
    private const val NOT_A_SECRET = "https://signal.me/#p/+15551234567"
  }
}
