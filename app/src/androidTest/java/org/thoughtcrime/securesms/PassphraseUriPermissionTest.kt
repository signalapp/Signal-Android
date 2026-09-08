package org.thoughtcrime.securesms

import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the URI grant bookkeeping that keeps a share intent usable across the passphrase prompt.
 *
 * Rather than exercising real grants -- which would require a second package to observe, since the app implicitly
 * holds permission on its own providers -- these tests wrap the context and record what
 * [PassphraseRequiredActivity.revokePreservedUriPermissions] targets. That makes the URI extraction and flag
 * selection observable, which is where the interesting edge cases live.
 */
@RunWith(AndroidJUnit4::class)
class PassphraseUriPermissionTest {

  companion object {
    private const val AUTHORITY = "content://org.thoughtcrime.securesms.test"

    private val READ = Intent.FLAG_GRANT_READ_URI_PERMISSION
    private val WRITE = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
  }

  private val appContext: Context = ApplicationProvider.getApplicationContext()

  /** Captures revocations instead of performing them. */
  private class RecordingContext(base: Context) : ContextWrapper(base) {
    val revoked = mutableListOf<Pair<Uri, Int>>()

    override fun revokeUriPermission(targetPackage: String?, uri: Uri, modeFlags: Int) {
      revoked += uri to modeFlags
    }
  }

  private fun contentUri(id: Int): Uri = Uri.parse("$AUTHORITY/$id")

  private fun revoke(intent: Intent?): List<Pair<Uri, Int>> {
    val recorder = RecordingContext(appContext)
    PassphraseRequiredActivity.revokePreservedUriPermissions(recorder, intent)
    return recorder.revoked
  }

  @Test
  fun givenNullIntent_whenIRevoke_thenIExpectNoRevocations() {
    assertThat(revoke(null)).isEmpty()
  }

  @Test
  fun givenIntentWithoutGrantFlags_whenIRevoke_thenIExpectNoRevocations() {
    val intent = Intent(Intent.ACTION_SEND).apply {
      putExtra(Intent.EXTRA_STREAM, contentUri(1))
    }

    assertThat(revoke(intent)).isEmpty()
  }

  @Test
  fun givenGrantFlagsButNoUris_whenIRevoke_thenIExpectNoRevocations() {
    val intent = Intent(Intent.ACTION_SEND).apply {
      addFlags(READ)
      putExtra(Intent.EXTRA_TEXT, "no uris here")
    }

    assertThat(revoke(intent)).isEmpty()
  }

  @Test
  fun givenIntentWithDataUri_whenIRevoke_thenIExpectThatUriRevoked() {
    val uri = contentUri(1)
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
      addFlags(READ)
    }

    assertThat(revoke(intent)).containsExactlyInAnyOrder(uri to READ)
  }

  @Test
  fun givenSingleStreamExtra_whenIRevoke_thenIExpectThatUriRevoked() {
    val uri = contentUri(1)
    val intent = Intent(Intent.ACTION_SEND).apply {
      addFlags(READ)
      putExtra(Intent.EXTRA_STREAM, uri)
    }

    assertThat(revoke(intent)).containsExactlyInAnyOrder(uri to READ)
  }

  @Test
  fun givenMultipleStreamExtras_whenIRevoke_thenIExpectEveryUriRevoked() {
    val first = contentUri(1)
    val second = contentUri(2)
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
      addFlags(READ)
      putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
    }

    assertThat(revoke(intent)).containsExactlyInAnyOrder(first to READ, second to READ)
  }

  @Test
  fun givenClipDataUris_whenIRevoke_thenIExpectEveryUriRevoked() {
    val first = contentUri(1)
    val second = contentUri(2)
    val intent = Intent(Intent.ACTION_SEND).apply {
      addFlags(READ)
      clipData = ClipData.newRawUri("first", first).apply {
        addItem(ClipData.Item(second))
      }
    }

    assertThat(revoke(intent)).containsExactlyInAnyOrder(first to READ, second to READ)
  }

  @Test
  fun givenUriNestedInClipDataIntent_whenIRevoke_thenIExpectNestedUriRevoked() {
    val outer = contentUri(1)
    val nested = contentUri(2)

    val nestedIntent = Intent(Intent.ACTION_VIEW, nested)
    val intent = Intent(Intent.ACTION_SEND).apply {
      addFlags(READ)
      putExtra(Intent.EXTRA_STREAM, outer)
      clipData = ClipData.newIntent("nested", nestedIntent)
    }

    assertThat(revoke(intent)).containsExactlyInAnyOrder(outer to READ, nested to READ)
  }

  @Test
  fun givenStreamExtraOnNestedIntent_whenIRevoke_thenIExpectNestedUriRevoked() {
    val nested = contentUri(1)

    val nestedIntent = Intent(Intent.ACTION_SEND).apply {
      putExtra(Intent.EXTRA_STREAM, nested)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
      addFlags(READ)
      clipData = ClipData.newIntent("nested", nestedIntent)
    }

    assertThat(revoke(intent)).containsExactlyInAnyOrder(nested to READ)
  }

  @Test
  fun givenNonContentSchemes_whenIRevoke_thenIExpectThemSkipped() {
    val content = contentUri(1)
    val intent = Intent(Intent.ACTION_SEND).apply {
      addFlags(READ)
      putParcelableArrayListExtra(
        Intent.EXTRA_STREAM,
        arrayListOf(
          content,
          Uri.parse("file:///sdcard/photo.jpg"),
          Uri.parse("https://signal.org/logo.png")
        )
      )
    }

    assertThat(revoke(intent)).containsExactlyInAnyOrder(content to READ)
  }

  @Test
  fun givenBothGrantFlags_whenIRevoke_thenIExpectEachFlagRevokedSeparately() {
    val uri = contentUri(1)
    val intent = Intent(Intent.ACTION_SEND).apply {
      addFlags(READ or WRITE)
      putExtra(Intent.EXTRA_STREAM, uri)
    }

    assertThat(revoke(intent)).containsExactlyInAnyOrder(uri to READ, uri to WRITE)
  }

  @Test
  fun givenOnlyWriteFlag_whenIRevoke_thenIExpectReadLeftAlone() {
    val uri = contentUri(1)
    val intent = Intent(Intent.ACTION_SEND).apply {
      addFlags(WRITE)
      putExtra(Intent.EXTRA_STREAM, uri)
    }

    assertThat(revoke(intent)).containsExactlyInAnyOrder(uri to WRITE)
  }

  @Test
  fun givenTheSameUriTwice_whenIRevoke_thenIExpectItRevokedOnce() {
    val uri = contentUri(1)
    val intent = Intent(Intent.ACTION_SEND, uri).apply {
      addFlags(READ)
      putExtra(Intent.EXTRA_STREAM, uri)
      clipData = ClipData.newRawUri("dupe", uri)
    }

    assertThat(revoke(intent)).containsExactlyInAnyOrder(uri to READ)
  }

  @Test
  fun givenIntentWithoutPreservedMarker_whenICheck_thenIExpectFalse() {
    assertThat(PassphraseRequiredActivity.hasPreservedUriPermissions(Intent())).isFalse()
  }

  @Test
  fun givenIntentCarryingPreservedMarker_whenICheck_thenIExpectTrue() {
    val prompt = Intent().apply {
      putExtra("next_intent_uri_grants_preserved", true)
    }

    assertThat(PassphraseRequiredActivity.hasPreservedUriPermissions(prompt)).isTrue()
  }
}
