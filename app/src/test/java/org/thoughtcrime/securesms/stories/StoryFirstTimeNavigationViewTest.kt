package org.thoughtcrime.securesms.stories

import android.app.Application
import android.graphics.drawable.Drawable
import android.os.Looper.getMainLooper
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.GlideRequest
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.visible

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StoryFirstTimeNavigationViewTest {

  @Rule
  @JvmField
  val mockitoRule: MockitoRule = MockitoJUnit.rule()

  private lateinit var testSubject: StoryFirstTimeNavigationView

  @Mock
  private lateinit var glideApp: MockedStatic<GlideApp>

  @Mock
  private lateinit var glideRequests: GlideRequests

  @Mock
  private lateinit var glideRequest: GlideRequest<Drawable>

  @Before
  fun setUp() {
    testSubject = StoryFirstTimeNavigationView(ContextThemeWrapper(ApplicationProvider.getApplicationContext(), org.thoughtcrime.securesms.R.style.Signal_DayNight))

    whenever(GlideApp.with(any<View>())).thenReturn(glideRequests)
    whenever(glideRequests.load(any<BlurHash>())).thenReturn(glideRequest)
    whenever(glideRequest.addListener(any())).thenReturn(glideRequest)
  }

  @Test
  @Config(sdk = [31])
  fun `Given sdk 31, when I create testSubject, then I expect overlay visible and blur hash not visible`() {
    shadowOf(getMainLooper()).idle()

    assertTrue(testSubject.findViewById<View>(org.thoughtcrime.securesms.R.id.edu_overlay).visible)
    assertFalse(testSubject.findViewById<View>(org.thoughtcrime.securesms.R.id.edu_blur_hash).visible)
  }

  @Test
  @Config(sdk = [30])
  fun `Given sdk 30, when I create testSubject, then I expect overlay visible and blur hash visible`() {
    shadowOf(getMainLooper()).idle()

    assertTrue(testSubject.findViewById<View>(org.thoughtcrime.securesms.R.id.edu_overlay).visible)
    assertTrue(testSubject.findViewById<View>(org.thoughtcrime.securesms.R.id.edu_blur_hash).visible)
  }

  @Test
  @Config(sdk = [31])
  fun `Given sdk 31 when I set blur hash, then blur has is visible`() {
    shadowOf(getMainLooper()).idle()

    testSubject.setBlurHash(BlurHash.parseOrNull("0000")!!)

    assertFalse(testSubject.findViewById<View>(org.thoughtcrime.securesms.R.id.edu_blur_hash).visible)
  }

  @Test
  @Config(sdk = [30])
  fun `Given sdk 30, when I set blur hash, then blur hash is loaded`() {
    shadowOf(getMainLooper()).idle()

    testSubject.setBlurHash(BlurHash.parseOrNull("0000")!!)

    val blurHashView = testSubject.findViewById<ImageView>(org.thoughtcrime.securesms.R.id.edu_blur_hash)
    verify(glideRequest).into(eq(blurHashView))
  }

  @Test
  @Config(sdk = [30])
  fun `Given sdk 30, when I set blur hash to null, then blur hash is hidden and cleared`() {
    shadowOf(getMainLooper()).idle()

    testSubject.setBlurHash(null)

    val blurHashView = testSubject.findViewById<ImageView>(org.thoughtcrime.securesms.R.id.edu_blur_hash)
    assertFalse(blurHashView.visible)
    verify(glideRequests).clear(blurHashView)
  }

  @Test
  @Config(sdk = [30])
  fun `Given sdk 30 and user has seen overlay, when I set blur hash, then nothing happens`() {
    shadowOf(getMainLooper()).idle()
    testSubject.callback = object : StoryFirstTimeNavigationView.Callback {
      override fun userHasSeenFirstNavigationView(): Boolean = true
      override fun onGotItClicked() = error("Unused")
      override fun onCloseClicked() = error("Unused")
    }

    testSubject.setBlurHash(BlurHash.parseOrNull("0000")!!)

    val blurHashView = testSubject.findViewById<ImageView>(org.thoughtcrime.securesms.R.id.edu_blur_hash)
    verify(glideRequest, never()).into(eq(blurHashView))
  }

  @Test
  @Config
  fun `When I hide then I expect not to be visible`() {
    testSubject.hide()

    assertFalse(testSubject.visible)
  }

  @Test
  @Config
  fun `Given I hide, when I show, then I expect to be visible`() {
    testSubject.hide()

    testSubject.show()

    assertTrue(testSubject.visible)
  }

  @Test
  @Config
  fun `Given I hide and user has seen overlay, when I show, then I expect to not be visible`() {
    testSubject.hide()
    testSubject.callback = object : StoryFirstTimeNavigationView.Callback {
      override fun userHasSeenFirstNavigationView(): Boolean = true
      override fun onGotItClicked() = error("Unused")
      override fun onCloseClicked() = error("Unused")
    }

    testSubject.show()

    assertFalse(testSubject.visible)
  }
}
