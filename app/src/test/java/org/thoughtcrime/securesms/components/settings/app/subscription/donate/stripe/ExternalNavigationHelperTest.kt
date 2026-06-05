package org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ExternalNavigationHelperTest {

  @Test
  fun `sanitizeWebIntent clears explicit component`() {
    val intent = Intent().apply {
      component = ComponentName("org.thoughtcrime.securesms", "org.thoughtcrime.securesms.FakeInternalActivity")
    }

    val sanitized = with(ExternalNavigationHelper) { intent.sanitizeWebIntent() }

    assertNull(sanitized.component)
  }

  @Test
  fun `sanitizeWebIntent clears selector`() {
    val intent = Intent().apply {
      selector = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
    }

    val sanitized = with(ExternalNavigationHelper) { intent.sanitizeWebIntent() }

    assertNull(sanitized.selector)
  }

  @Test
  fun `sanitizeWebIntent adds CATEGORY_BROWSABLE`() {
    val intent = Intent()

    val sanitized = with(ExternalNavigationHelper) { intent.sanitizeWebIntent() }

    assertTrue(sanitized.hasCategory(Intent.CATEGORY_BROWSABLE))
  }

  @Test
  fun `sanitizeWebIntent strips URI permission grant flags`() {
    val intent = Intent().apply {
      flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
    }

    val sanitized = with(ExternalNavigationHelper) { intent.sanitizeWebIntent() }

    assertFalse(sanitized.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    assertFalse(sanitized.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
    assertFalse(sanitized.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
    assertFalse(sanitized.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION != 0)
  }

  @Test
  fun `sanitizeWebIntent preserves unrelated flags`() {
    val intent = Intent().apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    val sanitized = with(ExternalNavigationHelper) { intent.sanitizeWebIntent() }

    assertTrue(sanitized.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    assertFalse(sanitized.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
  }

  @Test
  fun `sanitizeWebIntent preserves browser_fallback_url extra`() {
    val intent = Intent().apply {
      putExtra("browser_fallback_url", "https://example.com/fallback")
    }

    val sanitized = with(ExternalNavigationHelper) { intent.sanitizeWebIntent() }

    assertEquals("https://example.com/fallback", sanitized.getStringExtra("browser_fallback_url"))
  }

  @Test
  fun `parsed web intent URI loses explicit component after sanitization`() {
    val uri = "intent://x#Intent;component=org.thoughtcrime.securesms/.sharing.v2.ShareActivity;action=android.intent.action.SEND;end"
    val parsed = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
    assertNotNull("Test precondition: parsed URI should set the component", parsed.component)

    val sanitized = with(ExternalNavigationHelper) { parsed.sanitizeWebIntent() }

    assertNull(sanitized.component)
    assertTrue(sanitized.hasCategory(Intent.CATEGORY_BROWSABLE))
  }

  @Test
  fun `parsed web intent URI loses selector after sanitization`() {
    val uri = "intent://x#Intent;action=android.intent.action.VIEW;SEL;scheme=https;end"
    val parsed = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
    assertNotNull("Test precondition: parsed URI should set a selector", parsed.selector)

    val sanitized = with(ExternalNavigationHelper) { parsed.sanitizeWebIntent() }

    assertNull(sanitized.selector)
  }
}
